package org.yc.learn.rag.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.yc.learn.rag.domain.DocumentEntity;
import org.yc.learn.rag.domain.DocumentStatus;
import org.yc.learn.rag.repository.DocumentRepository;
import org.yc.learn.rag.service.VectorStoreService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 应用重启后，上一次没跑完的任务（PENDING / PROCESSING）已经丢失，统一标记为 FAILED，
 * 让用户可以在界面上点“重建”恢复，而不是永远卡在处理中。
 * <p>
 * 同时清理被打断任务可能写入的部分向量：重建时会先按 docId 删除，但提前清掉可以避免
 * 用户在重建前提问时检索到不完整的文档。
 *
 * @author zhengyanchuang
 */
@Configuration
public class StartupRecoveryConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupRecoveryConfig.class);

    @Bean
    @Order(2)
    public ApplicationRunner recoverInterruptedDocuments(DocumentRepository repository,
                                                         VectorStoreService vectorStoreService) {
        return args -> {
            List<DocumentStatus> unfinished = List.of(DocumentStatus.PENDING, DocumentStatus.PROCESSING);
            List<DocumentEntity> documents = repository.findAllByStatusIn(unfinished);
            if (documents.isEmpty()) {
                return;
            }

            for (DocumentEntity document : documents) {
                try {
                    vectorStoreService.deleteByDocId(document.getId());
                } catch (Exception e) {
                    log.warn("清理中断文档的残留向量失败：id={}", document.getId(), e);
                }
                boolean fileExists = Files.isRegularFile(Path.of(document.getStoredPath()));
                document.setStatus(DocumentStatus.FAILED);
                document.setChunkCount(0);
                document.setErrorMessage(fileExists
                        ? "应用重启，入库任务中断，请点击重建索引"
                        : "应用重启，入库任务中断且原始文件丢失，请删除后重新上传");
            }
            repository.saveAll(documents);
            log.warn("已将 {} 个未完成入库的文档标记为 FAILED", documents.size());
        };
    }
}
