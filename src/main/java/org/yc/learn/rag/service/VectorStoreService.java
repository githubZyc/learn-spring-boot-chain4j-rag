package org.yc.learn.rag.service;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.ai.MetadataKeys;
import org.yc.learn.rag.config.AppProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 向量库上的管理操作（按文档删除等）。
 *
 * @author zhengyanchuang
 */
@Service
public class VectorStoreService {

    private final QdrantClient qdrantClient;
    private final String collection;

    public VectorStoreService(QdrantClient qdrantClient, AppProperties properties) {
        this.qdrantClient = qdrantClient;
        this.collection = properties.qdrant().collection();
    }

    /**
     * 删除某个文档写入的全部 chunk。
     * <p>
     * 直接使用 Qdrant 原生 keyword 匹配，语义明确（payload 中 docId 是字符串）。
     */
    public void deleteByDocId(String docId) {
        Common.Filter filter = Common.Filter.newBuilder()
                .addMust(io.qdrant.client.ConditionFactory.matchKeyword(MetadataKeys.DOC_ID, docId))
                .build();
        try {
            qdrantClient.deleteAsync(collection, filter, Duration.ofSeconds(10))
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("删除向量时线程被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("删除文档 " + docId + " 的向量失败", e);
        }
    }
}
