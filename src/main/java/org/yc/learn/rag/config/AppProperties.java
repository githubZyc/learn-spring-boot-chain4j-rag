package org.yc.learn.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置，对应 application.yml 中的 app.*。
 *
 * @author zhengyanchuang
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String uploadDir,
        long maxFileSizeBytes,
        Sse sse,
        Qdrant qdrant,
        Rag rag,
        Memory memory,
        Schema schema) {

    public record Sse(long timeoutMs) {
    }

    public record Qdrant(String host, int grpcPort, boolean useTls, String collection) {
    }

    public record Rag(int chunkSize, int chunkOverlap, int embeddingBatchSize) {
    }

    public record Memory(int maxMessages) {
    }

    /**
     * 数据库知识库（Schema RAG）配置。
     */
    public record Schema(String collection, int maxResults, double minScore, Query query,
                         SchemaDataSource datasource) {
    }

    /**
     * NL2SQL 多表查询配置。
     *
     * @param maxTables     首轮检索并送入模型的表数量；模型还可以在重试中按需扩展
     * @param maxExpansions 模型引用了未加载表时的最大扩展重试次数
     */
    public record Query(int maxTables, int maxExpansions) {
    }

    /**
     * 用于 Schema 同步的 MySQL 连接；url 为空表示未配置。
     */
    public record SchemaDataSource(
            String url,
            String username,
            String password,
            String database,
            String defaultTimeColumn,
            String tableWhitelist,
            String extraSensitiveColumns,
            String tableAliases,
            String tableAliasesFile) {
    }
}
