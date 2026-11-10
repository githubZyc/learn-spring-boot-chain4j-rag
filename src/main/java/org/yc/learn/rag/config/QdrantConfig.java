package org.yc.learn.rag.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;

/**
 * Qdrant 没有官方 Spring Boot starter，这里手动创建客户端与向量库。
 *
 * @author zhengyanchuang
 */
@Configuration
public class QdrantConfig {

    private static final Logger log = LoggerFactory.getLogger(QdrantConfig.class);

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient(AppProperties properties) {
        AppProperties.Qdrant qdrant = properties.qdrant();
        log.info("连接 Qdrant：{}:{}（gRPC），集合：{}",
                qdrant.host(), qdrant.grpcPort(), qdrant.collection());
        return new QdrantClient(QdrantGrpcClient
                .newBuilder(qdrant.host(), qdrant.grpcPort(), qdrant.useTls())
                .build());
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(QdrantClient client, AppProperties properties) {
        return QdrantEmbeddingStore.builder()
                .client(client)
                .collectionName(properties.qdrant().collection())
                .build();
    }

    /**
     * 启动时确保集合存在，并校验维度与向量模型一致。
     * <p>
     * Qdrant 集合的维度创建后不可修改，维度不一致会直接启动失败，避免写入时才报错。
     */
    @Bean
    @Order(1)
    public ApplicationRunner qdrantCollectionInitializer(QdrantClient client,
                                                         EmbeddingModel embeddingModel,
                                                         AppProperties properties) {
        return args -> {
            String collection = properties.qdrant().collection();
            int dimension = embeddingModel.dimension();
            try {
                boolean exists = client.collectionExistsAsync(collection).get(10, TimeUnit.SECONDS);
                if (!exists) {
                    client.createCollectionAsync(collection,
                                    Collections.VectorParams.newBuilder()
                                            .setSize(dimension)
                                            .setDistance(Collections.Distance.Cosine)
                                            .build())
                            .get(30, TimeUnit.SECONDS);
                    log.info("已创建 Qdrant 集合 {}（size={}, distance=Cosine）", collection, dimension);
                    return;
                }

                Collections.CollectionInfo info = client.getCollectionInfoAsync(collection)
                        .get(10, TimeUnit.SECONDS);
                long size = info.getConfig().getParams().getVectorsConfig().getParams().getSize();
                if (size != dimension) {
                    throw new IllegalStateException(("""
                            Qdrant 集合 %s 的向量维度是 %d，但当前 embedding 模型维度是 %d。
                            Qdrant 不支持修改已有集合的维度，请删除集合后重启应用：
                            curl -X DELETE http://%s:6333/collections/%s
                            """).formatted(collection, size, dimension,
                            properties.qdrant().host(), collection));
                }
                log.info("Qdrant 集合 {} 已存在（size={}）", collection, size);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        "初始化 Qdrant 集合 " + collection + " 失败，请确认 Qdrant 已启动且 gRPC 端口可访问", e);
            }
        };
    }
}
