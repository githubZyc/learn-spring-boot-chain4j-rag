package org.yc.learn.rag.connector;

import java.util.List;

/**
 * 一次分页拉取的结果。
 * <p>
 * {@code nextCursor} 为下一页的起点（由连接器定义语义，例如自增主键或更新时间），
 * 调用方原样回传即可；{@code hasMore} 为 {@code false} 表示本次同步结束。
 *
 * @author zhengyanchuang
 */
public record ConnectorBatch(List<RawDocument> documents, String nextCursor, boolean hasMore) {

    public ConnectorBatch {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public static ConnectorBatch empty() {
        return new ConnectorBatch(List.of(), null, false);
    }
}
