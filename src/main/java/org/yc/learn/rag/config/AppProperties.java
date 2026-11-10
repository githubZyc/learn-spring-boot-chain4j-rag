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
        Memory memory) {

    public record Sse(long timeoutMs) {
    }

    public record Qdrant(String host, int grpcPort, boolean useTls, String collection) {
    }

    public record Rag(int chunkSize, int chunkOverlap, int embeddingBatchSize) {
    }

    public record Memory(int maxMessages) {
    }
}
