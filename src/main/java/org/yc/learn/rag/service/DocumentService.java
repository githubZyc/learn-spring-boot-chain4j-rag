package org.yc.learn.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.domain.DocumentEntity;
import org.yc.learn.rag.domain.DocumentStatus;
import org.yc.learn.rag.dto.DocumentDto;
import org.yc.learn.rag.exception.ApiException;
import org.yc.learn.rag.extractor.TextExtractorRegistry;
import org.yc.learn.rag.repository.DocumentRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档上传、列表、删除与重建索引。
 * <p>
 * 上传只负责校验 + 落盘 + 建记录，真正的解析入库交给 {@link IngestionService} 异步执行，
 * 前端通过轮询文档列表获取处理进度。
 *
 * @author zhengyanchuang
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Duration CANCEL_TIMEOUT = Duration.ofSeconds(60);

    private final DocumentRepository repository;
    private final IngestionService ingestionService;
    private final VectorStoreService vectorStoreService;
    private final TextExtractorRegistry textExtractorRegistry;
    private final AppProperties properties;

    public DocumentService(DocumentRepository repository,
                           IngestionService ingestionService,
                           VectorStoreService vectorStoreService,
                           TextExtractorRegistry textExtractorRegistry,
                           AppProperties properties) {
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.vectorStoreService = vectorStoreService;
        this.textExtractorRegistry = textExtractorRegistry;
        this.properties = properties;
    }

    public DocumentDto upload(MultipartFile file) {
        String fileName = sanitizeFileName(file);
        String extension = extensionOf(fileName);
        if (!textExtractorRegistry.supports(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE",
                    "支持的文件类型：" + supportedTypesText());
        }
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "上传的文件为空");
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "文件超过大小限制：" + properties.maxFileSizeBytes() + " 字节");
        }

        String id = UUID.randomUUID().toString();
        Path uploadDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        Path storedPath = uploadDir.resolve(id + "." + extension);
        try {
            Files.createDirectories(uploadDir);
            file.transferTo(storedPath);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORE_FAILED",
                    "保存上传文件失败", e);
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setFileName(fileName);
        entity.setFileType(extension.toUpperCase(Locale.ROOT));
        entity.setStoredPath(storedPath.toString());
        entity.setSizeBytes(file.getSize());
        entity.setStatus(DocumentStatus.PENDING);
        entity.setChunkCount(0);
        // 主键是手动赋值的，Spring Data 会走 merge，@PrePersist 作用在合并后的副本上，
        // 必须回查一次才能拿到 createdAt / updatedAt
        repository.save(entity);
        DocumentEntity saved = repository.findById(id).orElseThrow();

        ingestionService.submit(id);
        return DocumentDto.from(saved);
    }

    public List<DocumentDto> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(DocumentDto::from)
                .toList();
    }

    public DocumentDto get(String id) {
        return DocumentDto.from(findOrThrow(id));
    }

    public void delete(String id) {
        DocumentEntity entity = findOrThrow(id);
        // 先停掉后台任务，避免任务在删除后又把向量写回去；停不下来就不要删，防止变成孤儿数据
        if (!ingestionService.cancelAndAwait(id, CANCEL_TIMEOUT)) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_PROCESSING",
                    "文档正在入库，请稍后再试");
        }
        // 先删向量，失败则保留记录，允许稍后重试
        vectorStoreService.deleteByDocId(id);
        repository.delete(entity);
        deleteFileQuietly(entity.getStoredPath());
    }

    public DocumentDto reindex(String id) {
        DocumentEntity entity = findOrThrow(id);
        Path path = Path.of(entity.getStoredPath());
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.CONFLICT, "FILE_MISSING",
                    "原始文件已不存在，无法重建索引");
        }
        if (ingestionService.isProcessing(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCUMENT_PROCESSING",
                    "文档正在处理中，请稍后再试");
        }
        entity.setStatus(DocumentStatus.PENDING);
        entity.setErrorMessage(null);
        repository.save(entity);
        ingestionService.submit(id);
        return DocumentDto.from(repository.findById(id).orElseThrow());
    }

    private DocumentEntity findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                        "文档不存在：" + id));
    }

    private static String sanitizeFileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MISSING_FILE_NAME", "缺少文件名");
        }
        return Paths.get(original).getFileName().toString();
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String supportedTypesText() {
        return textExtractorRegistry.supportedTypes().stream()
                .map(type -> "." + type.toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining("、"));
    }

    private static void deleteFileQuietly(String storedPath) {
        try {
            Files.deleteIfExists(Path.of(storedPath));
        } catch (IOException e) {
            log.warn("删除文件失败（不影响文档删除）：{}", storedPath, e);
        }
    }
}
