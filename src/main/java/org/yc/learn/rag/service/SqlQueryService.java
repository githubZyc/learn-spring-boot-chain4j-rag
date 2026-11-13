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
import org.yc.learn.rag.dto.SqlTableRefDto;
import org.yc.learn.rag.exception.ApiException;
import org.yc.learn.rag.util.SensitiveMasker;
import org.yc.learn.rag.util.SqlValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 自然语言查数（NL2SQL）编排，支持多表关联。
 * <p>
 * 流程：检索候选表 → 把多张表结构一起交给模型 → 模型生成 SQL →
 * 若 SQL 引用了尚未加载的表，按需加载后重试 → 安全校验 → 只读执行 → 敏感字段脱敏。
 * <p>
 * 测试环境不做表级权限：只要表在当前库中存在就允许查询；仍然禁止写操作、注释、多语句，
 * 并强制外层 {@code LIMIT 200}。
 *
 * @author zhengyanchuang
 */
@Service
public class SqlQueryService {

    private static final Logger log = LoggerFactory.getLogger(SqlQueryService.class);

    private static final int MAX_ROWS = 200;
    private static final int QUERY_TIMEOUT_SECONDS = 10;
    private static final int SCHEMA_TEXT_LIMIT = 16000;
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
        return query(question, null);
    }

    /**
     * 限定表范围的查数；{@code tableWhitelist} 为空表示允许当前库的全部表。
     */
    public SqlQueryResponse query(String question, Set<String> tableWhitelist) {
        AppProperties.Schema schema = properties.schema();
        if (!SchemaDatasourceSupport.configured(schema)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEMA_DATASOURCE_NOT_CONFIGURED",
                    "未配置数据库连接，请设置 SCHEMA_DB_URL / SCHEMA_DB_USERNAME / SCHEMA_DB_PASSWORD");
        }

        AppProperties.Query queryConfig = schema.query();
        int maxTables = queryConfig == null ? 8 : Math.max(1, queryConfig.maxTables());
        int maxExpansions = queryConfig == null ? 2 : Math.max(0, queryConfig.maxExpansions());
        ConnectorConfig config = SchemaDatasourceSupport.connectorConfig(schema.datasource());

        // 首轮：向量检索 + 主表优先重排，取前 N 张表
        List<SchemaMatchDto> candidates = schemaSearchService.search(question, maxTables, tableWhitelist);
        if (candidates.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCHEMA_TABLE_NOT_FOUND",
                    "没有找到与问题匹配的表，请先同步表结构或换个问法");
        }
        Set<String> allowedTables = candidates.stream()
                .map(SchemaMatchDto::tableName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, SchemaDocument> schemas = loadSchemas(config, allowedTables);

        // 生成 → 检查引用表 → 缺表则加载并重试
        String generated = generateSql(question, schemas.values());
        SqlValidator.InspectedSql inspected = inspectSql(generated);
        Set<String> existingTables = tableWhitelist != null && !tableWhitelist.isEmpty()
                ? new LinkedHashSet<>(tableWhitelist)
                : null;

        for (int attempt = 0; attempt < maxExpansions; attempt++) {
            Set<String> missing = missingTables(inspected.tables(), allowedTables);
            if (missing.isEmpty()) {
                break;
            }
            if (existingTables == null) {
                existingTables = schemaExtractor.listTableNames(config);
            }
            if (tableWhitelist != null && !tableWhitelist.isEmpty()) {
                // 限定场景下不允许扩展到白名单之外的表
                Set<String> whitelistLower = tableWhitelist.stream()
                        .map(table -> table.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                boolean outside = inspected.tables().stream()
                        .anyMatch(table -> !whitelistLower.contains(table.toLowerCase(Locale.ROOT)));
                if (outside) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                            "SQL 引用了当前场景不允许查询的表");
                }
            }
            LinkedHashSet<String> resolved = new LinkedHashSet<>();
            for (String table : missing) {
                String canonical = findIgnoreCase(existingTables, table);
                if (canonical == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                            "SQL 引用了不存在的表：" + table);
                }
                resolved.add(canonical);
            }
            log.info("SQL 引用了未加载的表，自动扩展：{}", resolved);
            allowedTables.addAll(resolved);
            schemas = loadSchemas(config, allowedTables);
            generated = generateSql(question, schemas.values());
            inspected = inspectSql(generated);
        }

        Set<String> stillMissing = missingTables(inspected.tables(), allowedTables);
        if (!stillMissing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                    "SQL 引用了未加载的表：" + String.join(",", stillMissing));
        }

        // 测试环境不做表级权限：只校验引用的表在库中存在
        if (existingTables == null) {
            existingTables = schemaExtractor.listTableNames(config);
        }
        for (String table : inspected.tables()) {
            if (findIgnoreCase(existingTables, table) == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                        "SQL 引用了不存在的表：" + table);
            }
        }

        SqlValidator.ValidatedSql validated;
        try {
            validated = SqlValidator.validateAndNormalize(inspected.cleanedSql(), existingTables);
        } catch (IllegalArgumentException e) {
            log.warn("SQL 安全校验未通过：tables={}, generated={}, reason={}",
                    inspected.tables(), generated, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                    "生成的 SQL 未通过安全校验：" + e.getMessage(), e);
        }

        long start = System.nanoTime();
        QueryResult result;
        try {
            result = queryExecutor.execute(config, validated.executableSql(), MAX_ROWS, QUERY_TIMEOUT_SECONDS);
        } catch (RuntimeException e) {
            log.error("SQL 执行失败：tables={}, sql={}", inspected.tables(), validated.displaySql(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SQL_EXECUTION_FAILED",
                    "查询执行失败，请检查字段或时间条件", e);
        }
        long costMs = (System.nanoTime() - start) / 1_000_000;

        Map<String, SchemaDocument> finalSchemas = schemas;
        List<SqlTableRefDto> usedTables = inspected.tables().stream()
                .map(table -> findSchema(finalSchemas, table))
                .filter(Objects::nonNull)
                .map(document -> new SqlTableRefDto(document.tableName(), document.tableComment()))
                .toList();
        List<String> sensitiveColumns = inspected.tables().stream()
                .map(table -> findSchema(finalSchemas, table))
                .filter(Objects::nonNull)
                .flatMap(document -> document.sensitiveColumns().stream())
                .distinct()
                .toList();

        MaskedResult masked = mask(result, sensitiveColumns);
        log.info("数据库查数完成：tables={}, rows={}, costMs={}, sql={}",
                usedTables.stream().map(SqlTableRefDto::tableName).toList(),
                result.rows().size(), costMs, validated.displaySql());

        return new SqlQueryResponse(
                question,
                usedTables,
                validated.displaySql(),
                result.columns(),
                masked.rows(),
                result.rows().size(),
                costMs,
                masked.maskedColumns());
    }

    /**
     * 加载一批表的完整结构；缺少任一表都视为失败，避免模型基于残缺上下文生成 SQL。
     */
    private Map<String, SchemaDocument> loadSchemas(ConnectorConfig config, Set<String> tableNames) {
        List<SchemaDocument> documents = schemaExtractor.extractTables(config, tableNames);
        Map<String, SchemaDocument> byName = new LinkedHashMap<>();
        for (SchemaDocument document : documents) {
            byName.put(document.tableName(), document);
        }
        Set<String> missing = missingTables(tableNames, byName.keySet());
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SCHEMA_TABLE_NOT_FOUND",
                    "表不存在或未授权：" + String.join(",", missing));
        }
        return byName;
    }

    private String generateSql(String question, Collection<SchemaDocument> schemas) {
        String schemaText = schemas.stream()
                .map(SchemaDocument::toEmbeddingText)
                .collect(Collectors.joining("\n---\n"));
        if (schemaText.length() > SCHEMA_TEXT_LIMIT) {
            schemaText = schemaText.substring(0, SCHEMA_TEXT_LIMIT) + "\n...（表结构过多，已截断）";
        }
        String tableList = schemas.stream()
                .map(document -> document.tableComment().isBlank()
                        ? document.tableName()
                        : document.tableName() + "（" + document.tableComment() + "）")
                .collect(Collectors.joining("、"));

        String prompt = """
                你是 MySQL 5.7/8.0 的只读 SQL 生成器。
                可以使用下面列出的多张表，允许 JOIN，表数量不限；只能使用列出的表和字段，不要编造。
                规则：
                1. 只生成一条 SELECT 查询；
                2. 禁止 INSERT / UPDATE / DELETE / DROP / ALTER / TRUNCATE 等写操作；
                3. 涉及“今天 / 最近 N 天”时，优先使用“默认时间字段”，没有则使用表中最合适的时间字段；
                4. 不要自己加 LIMIT，系统会自动限制返回行数；
                5. 多表关联请使用 JOIN，不要使用逗号连接表；
                6. 不要使用 CTE（WITH）、窗口函数等 MySQL 5.7 不支持的语法；
                7. 只输出 SQL，不要解释，不要 markdown 代码块。

                可用表：%s

                表结构：
                %s

                用户问题：%s
                """.formatted(tableList, schemaText, question);
        try {
            String output = collect(chatModel.chat(prompt));
            if (output == null || output.isBlank()) {
                throw new IllegalStateException("模型没有返回 SQL");
            }
            return output;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成 SQL 失败：tables={}", schemas.stream().map(SchemaDocument::tableName).toList(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SQL_GENERATION_FAILED",
                    "生成 SQL 失败，请稍后重试", e);
        }
    }

    /**
     * 基础校验失败时转成稳定的业务错误码，避免把 IllegalArgumentException 暴露成 500。
     */
    private static SqlValidator.InspectedSql inspectSql(String generated) {
        try {
            return SqlValidator.inspect(generated);
        } catch (IllegalArgumentException e) {
            log.warn("SQL 基础校验未通过：generated={}, reason={}", generated, e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "SQL_REJECTED",
                    "生成的 SQL 未通过安全校验：" + e.getMessage(), e);
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

    private static Set<String> missingTables(Collection<String> referenced, Collection<String> allowed) {
        Set<String> allowedLower = allowed.stream()
                .map(table -> table.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        for (String table : referenced) {
            if (!allowedLower.contains(table.toLowerCase(Locale.ROOT))) {
                missing.add(table);
            }
        }
        return missing;
    }

    private static String findIgnoreCase(Collection<String> candidates, String target) {
        return candidates.stream()
                .filter(candidate -> candidate.equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);
    }

    private static SchemaDocument findSchema(Map<String, SchemaDocument> schemas, String table) {
        return schemas.values().stream()
                .filter(document -> document.tableName().equalsIgnoreCase(table))
                .findFirst()
                .orElse(null);
    }

    private record MaskedResult(List<List<String>> rows, List<String> maskedColumns) {
    }
}
