package org.yc.learn.rag.dto;

import org.yc.learn.rag.domain.DocumentEntity;
import org.yc.learn.rag.domain.DocumentStatus;

import java.time.Instant;

/**
 * 文档列表 / 详情返回体。
 *
 * @author zhengyanchuang
 */
public record DocumentDto(
        String id,
        String fileName,
        String fileType,
        long sizeBytes,
        DocumentStatus status,
        int chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt) {

    public static DocumentDto from(DocumentEntity entity) {
        return new DocumentDto(
                entity.getId(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getSizeBytes(),
                entity.getStatus(),
                entity.getChunkCount(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
