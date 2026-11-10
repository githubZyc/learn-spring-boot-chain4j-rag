package org.yc.learn.rag.service;

import com.alibaba.dashscope.utils.JsonUtils;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.ai.MetadataKeys;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.config.ManagedVirtualThreadExecutor;
import org.yc.learn.rag.domain.DocumentEntity;
import org.yc.learn.rag.domain.DocumentStatus;
import org.yc.learn.rag.repository.DocumentRepository;
import org.yc.learn.rag.util.TextNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析、切分、向量化并写入 Qdrant。
 * <p>
 * 入库在后台线程执行，上传接口立即返回。删除 / 重建通过 {@link #cancelAndAwait} 与任务串行化，
 * 避免「删除后后台任务又把向量写回去」的竞态。
 *
 * @author zhengyanchuang
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository repository;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final VectorStoreService vectorStoreService;
    private final AppProperties properties;
    private final ManagedVirtualThreadExecutor ingestionExecutor;

    /** 正在处理中的文档任务，用于判断是否重复提交、以及删除时等待 */
    private final Map<String, CompletableFuture<Void>> tasks = new ConcurrentHashMap<>();
    /** 被删除的文档 ID，任务在写入前后检查并尽快退出 */
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    public IngestionService(DocumentRepository repository,
                            EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            VectorStoreService vectorStoreService,
                            AppProperties properties,
                            @Qualifier("ingestionExecutor") ManagedVirtualThreadExecutor ingestionExecutor) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.vectorStoreService = vectorStoreService;
        this.properties = properties;
        this.ingestionExecutor = ingestionExecutor;
    }

    /**
     * 提交后台入库。已经处于处理中的文档会被忽略。
     */
    public void submit(String docId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (tasks.putIfAbsent(docId, future) != null) {
            log.debug("文档已在处理中，忽略重复提交：{}", docId);
            return;
        }
        ingestionExecutor.execute(() -> {
            try {
                ingest(docId);
            } catch (Exception e) {
                log.error("后台入库线程异常：docId={}", docId, e);
            } finally {
                tasks.remove(docId);
                cancelled.remove(docId);
                future.complete(null);
            }
        });
    }

    public boolean isProcessing(String docId) {
        return tasks.containsKey(docId);
    }

    /**
     * 取消并等待该文档的入库任务结束。返回 false 表示在超时时间内仍未结束。
     */
    public boolean cancelAndAwait(String docId, Duration timeout) {
        cancelled.add(docId);
        CompletableFuture<Void> future = tasks.get(docId);
        if (future == null) {
            // 没有正在运行的任务，取消标记不需要保留
            cancelled.remove(docId);
            return true;
        }
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            cancelled.remove(docId);
            return true;
        } catch (Exception e) {
            log.warn("等待文档入库任务结束超时：docId={}", docId);
            return false;
        }
    }

    private void ingest(String docId) {
        DocumentEntity entity = repository.findById(docId).orElse(null);
        if (entity == null || cancelled.contains(docId)) {
            return;
        }

        entity.setStatus(DocumentStatus.PROCESSING);
        entity.setErrorMessage(null);
        repository.save(entity);

        try {
            List<TextSegment> segments = split(entity);
            if (cancelled.contains(docId)) {
                return;
            }

            // 重试 / 重建索引前先清掉旧向量，避免 chunk 重复
            vectorStoreService.deleteByDocId(docId);
            if (cancelled.contains(docId)) {
                return;
            }
            storeInBatches(segments);

            if (cancelled.contains(docId)) {
                // 删除请求会等待本任务结束后再清理向量，这里不用重复处理
                return;
            }
            if (!repository.existsById(docId)) {
                return;
            }

            entity.setStatus(DocumentStatus.READY);
            entity.setChunkCount(segments.size());
            repository.save(entity);
            log.info("文档入库完成：id={}, file={}, chunks={}",
                    docId, entity.getFileName(), segments.size());
        } catch (Exception e) {
            log.error("文档入库失败：id={}, file={}", docId, entity.getFileName(), e);
            try {
                vectorStoreService.deleteByDocId(docId);
            } catch (Exception cleanupError) {
                log.warn("清理失败文档的向量时出错：id={}", docId, cleanupError);
            }
            if (cancelled.contains(docId) || !repository.existsById(docId)) {
                return;
            }
            entity.setStatus(DocumentStatus.FAILED);
            entity.setChunkCount(0);
            entity.setErrorMessage(truncate(rootMessage(e), 1000));
            repository.save(entity);
        }
    }

    private List<TextSegment> split(DocumentEntity entity) throws IOException {
        Path path = Path.of(entity.getStoredPath());
        String text = TextNormalizer.normalize(Files.readString(path, StandardCharsets.UTF_8));
        if (TextNormalizer.isBlank(text)) {
            throw new IllegalArgumentException("文档内容为空");
        }

        AppProperties.Rag rag = properties.rag();
        Document document = Document.from(text, Metadata.from(Map.of(
                MetadataKeys.DOC_ID, entity.getId(),
                MetadataKeys.FILE_NAME, entity.getFileName())));

        List<TextSegment> segments = DocumentSplitters
                .recursive(rag.chunkSize(), rag.chunkOverlap())
                .split(document)
                .stream()
                .filter(segment -> !TextNormalizer.isBlank(segment.text()))
                .toList();

        if (segments.isEmpty()) {
            throw new IllegalStateException("文档切分后没有有效内容");
        }
        return segments;
    }

    private void storeInBatches(List<TextSegment> segments) {
        int batchSize = Math.max(1, properties.rag().embeddingBatchSize());
        for (int from = 0; from < segments.size(); from += batchSize) {
            List<TextSegment> batch = new ArrayList<>(
                    segments.subList(from, Math.min(from + batchSize, segments.size())));
            List<Embedding> embeddings = embeddingModel.embedAll(batch).content();
            embeddingStore.addAll(embeddings, batch);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
