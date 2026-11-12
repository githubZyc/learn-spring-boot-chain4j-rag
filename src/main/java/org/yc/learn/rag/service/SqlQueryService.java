package org.yc.learn.rag.service;

import dev.langchain4j.model.chat.StreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.connector.ConnectorConfig;
import org.yc.learn.rag.connector.SchemaDocument;
import org.yc.learn.rag.connector.mysql.MySqlQueryExecutor;
import org.yc.learn.rag.connector.mysql.MySqlSchemaExtractor;
import org.yc.learn.rag.connector.mysql.QueryResult;
import org.yc.learn.rag.dto.SchemaMatchDto;
import org.yc.learn.rag.dto.SqlQueryResponse;
import org.yc.learn.rag.exception.ApiException;
import org.yc.learn.rag.util.SensitiveMasker;
import org.yc.learn.rag.util.SqlValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 自然语言查数（NL2SQL）编排。
 * <p>
 * 流程：找表 → 读取单表最新结构 → 模型生成 SQL → 安全校验 → 只读执行 → 敏感字段脱敏。
 * 模型生成的 SQL 不直接执行，必须经过 {@link SqlValidator}。
 *
 * @author zhengyanchuang
 */
@Service
public class SqlQueryService {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryService.class);

    private static final int MAX_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_CANDIDATES = 3;
    private static final int SCHEMA_TEXT_LIMIT = 6000;
    private static final long SQL_GENERATION_TIMEOUT_SECONDS = 60;

    private final SchemaSearchService schemaSearchService;
    private final MySqlSchemaExtractor schemaExtractor;
    private final MySqlQueryExecutor queryExecutor;
    private final StreamingChatModel chatModel;
    private final AppProperties properties;

    public SqlQueryService(SchemaSearchService schemaSearchService,
                           MySqlSchemaExtractor schemaExtractor,
                           MySqlQueryExecutor queryExecutor,
                           StreamingChatModel chatModel,
                           AppProperties properties) {
        this.schemaSearchService = schemaSearchService;
        this.schemaExtractor = schemaExtractor;
        this.queryExecutor = queryExecutor;
        this.chatModel = chatModel;
        this.properties = properties;
    }

    public SqlQueryResponse query(String question) {
        AppProperties.Schema schema = properties.schema();
        if (!SchemaDatasourceSupport.configured(schema)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEMA_DATASOURCE_NOT_CONFIGURED",
                    "未配置数据库连接，请设置 SCHEMA_DB_URL / SCHEMA_DB_USERNAME / SCHEMA_DB_PASSWORD");
        }

        List<SchemaMatchDto> candidates = schemaSearchService.search(question, MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCHEMA_TABLE_NOT_FOUND",
                    "没有找到与问题匹配的表，请先同步表结构或换个问法");
        }
        String tableName = pickTable(question, candidates);

        ConnectorConfig config = SchemaDatasourceSupport.connectorConfig(schema.datasource());
        SchemaDocument document;
        try {
            document = schemaExtractor.extractTable(config, tableName);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEMA_TABLE_NOT_FOUND", e.getMessage(), e);
        }

        String generated = generateSql(question, document);
        SqlValidator.ValidatedSql validated;
        try {
            validated = SqlValidator.validateAndNormalize(generated, tableName);
        } catch (IllegalArgumentException e) {
            log.warn("SQL 安全校验未通过：table={}, generated={}, reason={}",
                    tableName, generated, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                    "生成的 SQL 未通过安全校验：" + e.getMessage(), e);
        }

        long start = System.nanoTime();
        QueryResult result;
        try {
            result = queryExecutor.execute(config, validated.executableSql(), MAX_ROWS, QUERY_TIMEOUT_SECONDS);
        } catch (RuntimeException e) {
            log.error("SQL 执行失败：table={}, sql={}", tableName, validated.displaySql(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SQL_EXECUTION_FAILED",
                    "查询执行失败，请检查字段或时间条件", e);
        }
        long costMs = (System.nanoTime() - start) / 1_000_000;

        MaskedResult masked = mask(result, document.sensitiveColumns());
        log.info("数据库查数完成：table={}, rows={}, costMs={}, sql={}",
                tableName, result.rows().size(), costMs, validated.displaySql());

        return new SqlQueryResponse(
                question,
                tableName,
                document.tableComment(),
                validated.displaySql(),
                result.columns(),
                masked.rows(),
                result.rows().size(),
                costMs,
                masked.maskedColumns());
    }

    /**
     * 用户问题里明确出现了候选表名时优先使用，否则用重排后的第一张表。
     */
    private static String pickTable(String question, List<SchemaMatchDto> candidates) {
        String lower = question.toLowerCase(Locale.ROOT);
        for (SchemaMatchDto candidate : candidates) {
            if (lower.contains(candidate.tableName().toLowerCase(Locale.ROOT))) {
                return candidate.tableName();
            }
        }
        return candidates.get(0).tableName();
    }

    private String generateSql(String question, SchemaDocument document) {
        String schemaText = document.toEmbeddingText();
        if (schemaText.length() > SCHEMA_TEXT_LIMIT) {
            schemaText = schemaText.substring(0, SCHEMA_TEXT_LIMIT) + "\n...（字段过多，已截断）";
        }
        String prompt = """
                你是 MySQL 5.7/8.0 的只读 SQL 生成器。只能使用下面给出的表结构，不要编造字段。
                规则：
                1. 只生成一条 SELECT 查询；
                2. 禁止 INSERT / UPDATE / DELETE / DROP / ALTER / TRUNCATE 等写操作；
                3. 涉及“今天 / 最近 N 天”时，优先使用“默认时间字段”，没有则使用表中最合适的时间字段；
                4. 不要自己加 LIMIT，系统会自动限制返回行数；
                5. 不要使用 CTE（WITH）、窗口函数等 MySQL 5.7 不支持的语法；
                6. 只输出 SQL，不要解释，不要 markdown 代码块。

                表结构：
                %s

                用户问题：%s
                """.formatted(schemaText, question);
        try {
            String output = collect(chatModel.chat(prompt));
            if (output == null || output.isBlank()) {
                throw new IllegalStateException("模型没有返回 SQL");
            }
            return output;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成 SQL 失败：table={}", document.tableName(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SQL_GENERATION_FAILED",
                    "生成 SQL 失败，请稍后重试", e);
        }
    }

    /**
     * 把流式输出的 token 收齐成完整字符串；流式接口没有同步返回。
     */
    private static String collect(Flow.Publisher<String> publisher) throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                text.append(item);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(text.toString());
            }
        });
        return future.get(SQL_GENERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static MaskedResult mask(QueryResult result, List<String> sensitiveColumns) {
        Set<String> sensitive = sensitiveColumns.stream()
                .map(column -> column.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < result.columns().size(); i++) {
            if (sensitive.contains(result.columns().get(i).toLowerCase(Locale.ROOT))) {
                indexes.add(i);
            }
        }
        if (indexes.isEmpty()) {
            return new MaskedResult(result.rows(), List.of());
        }

        List<List<String>> maskedRows = new ArrayList<>(result.rows().size());
        for (List<String> row : result.rows()) {
            List<String> copy = new ArrayList<>(row);
            for (int index : indexes) {
                copy.set(index, SensitiveMasker.mask(result.columns().get(index), row.get(index)));
            }
            maskedRows.add(copy);
        }
        List<String> maskedColumns = indexes.stream().map(result.columns()::get).toList();
        return new MaskedResult(maskedRows, maskedColumns);
    }

    private record MaskedResult(List<List<String>> rows, List<String> maskedColumns) {
    }
}
