package org.yc.learn.rag.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.ai.MetadataKeys;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.config.ManagedVirtualThreadExecutor;
import org.yc.learn.rag.connector.SchemaDocument;
import org.yc.learn.rag.connector.mysql.MySqlSchemaExtractor;
import org.yc.learn.rag.dto.SchemaStatusDto;
import org.yc.learn.rag.exception.ApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 把数据库表结构向量化写入独立的 schema 集合。
 * <p>
 * 同步与文档入库一样在后台线程执行，接口立即返回 {@code RUNNING}，前端轮询状态。
 * 一表一文档、不做切分；重建时先清空 schema 集合，避免已删除的表残留。
 *
 * @author zhengyanchuang
 */
@Service
public class SchemaIndexService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIndexService.class);

    private final MySqlSchemaExtractor schemaExtractor;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> schemaEmbeddingStore;
    private final AppProperties properties;
    private final ManagedVirtualThreadExecutor schemaExecutor;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Progress> progress = new AtomicReference<>(Progress.idle());

    public SchemaIndexService(MySqlSchemaExtractor schemaExtractor,
                              EmbeddingModel embeddingModel,
                              @Qualifier("schemaEmbeddingStore") EmbeddingStore<TextSegment> schemaEmbeddingStore,
                              AppProperties properties,
                              @Qualifier("schemaExecutor") ManagedVirtualThreadExecutor schemaExecutor) {
        this.schemaExtractor = schemaExtractor;
        this.embeddingModel = embeddingModel;
        this.schemaEmbeddingStore = schemaEmbeddingStore;
        this.properties = properties;
        this.schemaExecutor = schemaExecutor;
    }

    /**
     * 当前同步状态；未配置数据源时 {@code configured=false}。
     */
    public SchemaStatusDto status() {
        AppProperties.Schema schema = properties.schema();
        if (schema == null) {
            return new SchemaStatusDto(false, null, "schema_kb", "IDLE", 0, null, null, null);
        }
        boolean configured = SchemaDatasourceSupport.configured(schema);
        String database = configured ? SchemaDatasourceSupport.database(schema.datasource()) : null;
        Progress current = progress.get();
        return new SchemaStatusDto(
                configured,
                database,
                schema.collection(),
                current.status(),
                current.tableCount(),
                current.error(),
                current.startedAt(),
                current.finishedAt());
    }

    /**
     * 提交后台同步；已在运行时直接返回当前状态，保证幂等。
     */
    public SchemaStatusDto submitSync() {
        AppProperties.Schema schema = properties.schema();
        if (!SchemaDatasourceSupport.configured(schema)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCHEMA_DATASOURCE_NOT_CONFIGURED",
                    "未配置数据库连接，请设置 SCHEMA_DB_URL / SCHEMA_DB_USERNAME / SCHEMA_DB_PASSWORD");
        }
        if (!running.compareAndSet(false, true)) {
            return status();
        }

        String database = SchemaDatasourceSupport.database(schema.datasource());
        Instant startedAt = Instant.now();
        progress.set(new Progress("RUNNING", 0, null, startedAt, null));

        schemaExecutor.execute(() -> {
            try {
                int tableCount = sync(schema.datasource());
                progress.set(new Progress("READY", tableCount, null, startedAt, Instant.now()));
                log.info("Schema 同步完成：database={}, tables={}", database, tableCount);
            } catch (Exception e) {
                log.error("Schema 同步失败：database={}", database, e);
                progress.set(new Progress("FAILED", 0, truncate(rootMessage(e), 500), startedAt, Instant.now()));
            } finally {
                running.set(false);
            }
        });
        return status();
    }

    private int sync(AppProperties.SchemaDataSource datasource) {
        List<SchemaDocument> documents = schemaExtractor.extract(
                SchemaDatasourceSupport.connectorConfig(datasource));

        // 全量重建：先清空 schema 集合，已删除的表不会残留
        schemaEmbeddingStore.removeAll(MetadataFilterBuilder
                .metadataKey(MetadataKeys.SOURCE_TYPE)
                .isEqualTo(MetadataKeys.SOURCE_SCHEMA));

        int batchSize = Math.max(1, properties.rag().embeddingBatchSize());
        for (int from = 0; from < documents.size(); from += batchSize) {
            int to = Math.min(from + batchSize, documents.size());
            List<SchemaDocument> batch = documents.subList(from, to);
            List<String> ids = batch.stream().map(SchemaIndexService::pointId).toList();
            List<TextSegment> segments = batch.stream().map(SchemaIndexService::toSegment).toList();
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            schemaEmbeddingStore.addAll(ids, embeddings, segments);
            log.info("Schema 向量写入进度：{}/{}", to, documents.size());
        }
        return documents.size();
    }

    private static TextSegment toSegment(SchemaDocument document) {
        Metadata metadata = Metadata.from(Map.of(
                MetadataKeys.SOURCE_TYPE, MetadataKeys.SOURCE_SCHEMA,
                MetadataKeys.SCHEMA_DATABASE, document.database(),
                MetadataKeys.TABLE_NAME, document.tableName(),
                MetadataKeys.TABLE_COMMENT, document.tableComment(),
                MetadataKeys.DEFAULT_TIME_COLUMN, document.defaultTimeColumn(),
                MetadataKeys.SENSITIVE_COLUMNS, String.join(",", document.sensitiveColumns())));
        return TextSegment.from(document.toEmbeddingText(), metadata);
    }

    /**
     * 稳定 ID：同一张表重复同步会覆盖同一个向量点，而不是产生重复数据。
     */
    private static String pointId(SchemaDocument document) {
        String key = "schema:" + document.database() + ":" + document.tableName();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record Progress(String status, int tableCount, String error, Instant startedAt, Instant finishedAt) {

        private static Progress idle() {
            return new Progress("IDLE", 0, null, null, null);
        }
    }
}
