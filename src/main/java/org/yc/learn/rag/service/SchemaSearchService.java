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
    private static final int CANDIDATE_MULTIPLIER = 4;
    private static final int MIN_CANDIDATES = 20;
    private static final int MAX_CANDIDATES = 50;

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

        // 多取一些候选再重排，避免主表被同族明细表挤出前 N
        int candidateCount = Math.min(MAX_CANDIDATES,
                Math.max(MIN_CANDIDATES, limit * CANDIDATE_MULTIPLIER));

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(candidateCount)
                .minScore(minScore)
                .filter(MetadataFilterBuilder.metadataKey(MetadataKeys.SOURCE_TYPE)
                        .isEqualTo(MetadataKeys.SOURCE_SCHEMA))
                .build();

        List<SchemaMatchDto> candidates = schemaEmbeddingStore.search(request).matches().stream()
                .map(SchemaMatchDto::from)
                .toList();
        return SchemaReranker.rerank(question, candidates, limit);
    }
}
