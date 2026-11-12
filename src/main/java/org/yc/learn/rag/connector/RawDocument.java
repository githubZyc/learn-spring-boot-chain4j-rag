package org.yc.learn.rag.connector;

import java.time.Instant;
import java.util.Map;

/**
 * 连接器产出的统一文档模型，进入后续规范化 / 切分 / 向量化链路。
 * <p>
 * {@code externalId} 用于生成稳定的文档 ID，保证重复同步不产生重复向量；
 * 具体由连接器决定（例如 MySQL 用「表名#主键」）。
 *
 * @author zhengyanchuang
 */
public record RawDocument(
        String externalId,
        String title,
        String content,
        Map<String, String> metadata,
        Instant updatedAt) {

    public RawDocument {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
