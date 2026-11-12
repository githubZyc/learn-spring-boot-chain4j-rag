package org.yc.learn.rag.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.ai.MetadataKeys;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.dto.SchemaMatchDto;

import java.util.List;

/**
 * 在 schema 集合中按自然语言检索候选表。
 * <p>
 * 这是「找表」而不是「查数」：只返回表结构与相似度，不读取任何业务数据。
 *
 * @author zhengyanchuang
 */
@Service
public class SchemaSearchService {

    private static final int MAX_RESULTS_LIMIT = 20;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> schemaEmbeddingStore;
    private final AppProperties properties;

    public SchemaSearchService(EmbeddingModel embeddingModel,
                               @Qualifier("schemaEmbeddingStore") EmbeddingStore<TextSegment> schemaEmbeddingStore,
                               AppProperties properties) {
        this.embeddingModel = embeddingModel;
        this.schemaEmbeddingStore = schemaEmbeddingStore;
        this.properties = properties;
    }

    public List<SchemaMatchDto> search(String question, Integer maxResults) {
        int defaultResults = properties.schema() == null ? 5 : properties.schema().maxResults();
        double minScore = properties.schema() == null ? 0.35 : properties.schema().minScore();
        int limit = maxResults == null
                ? defaultResults
                : Math.max(1, Math.min(maxResults, MAX_RESULTS_LIMIT));

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(limit)
                .minScore(minScore)
                .filter(MetadataFilterBuilder.metadataKey(MetadataKeys.SOURCE_TYPE)
                        .isEqualTo(MetadataKeys.SOURCE_SCHEMA))
                .build();

        return schemaEmbeddingStore.search(request).matches().stream()
                .map(SchemaMatchDto::from)
                .toList();
    }
}
