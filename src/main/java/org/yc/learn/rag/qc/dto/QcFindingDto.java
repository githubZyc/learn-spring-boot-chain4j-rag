package org.yc.learn.rag.qc.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yc.learn.rag.qc.domain.QcFindingEntity;
import org.yc.learn.rag.qc.domain.QcFindingStatus;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * 质控异常返回体。
 *
 * @author zhengyanchuang
 */
public record QcFindingDto(
        Long id,
        String ruleCode,
        String ruleName,
        QcSeverity severity,
        QcFindingStatus status,
        long recordId,
        String recordNo,
        String departId,
        String departName,
        String doctorId,
        String doctorName,
        LocalDate surgeryDate,
        String title,
        String detail,
        Map<String, Object> evidence,
        String owner,
        Instant firstFoundAt,
        Instant lastFoundAt,
        Instant resolvedAt,
        String resolvedBy,
        String resolveNote) {

    public static QcFindingDto from(QcFindingEntity entity, ObjectMapper objectMapper) {
        return new QcFindingDto(
                entity.getId(),
                entity.getRuleCode(),
                entity.getRuleName(),
                entity.getSeverity(),
                entity.getStatus(),
                entity.getRecordId(),
                entity.getRecordNo(),
                entity.getDepartId(),
                entity.getDepartName(),
                entity.getDoctorId(),
                entity.getDoctorName(),
                entity.getSurgeryDate(),
                entity.getTitle(),
                entity.getDetail(),
                parseEvidence(entity.getEvidenceJson(), objectMapper),
                entity.getOwner(),
                entity.getFirstFoundAt(),
                entity.getLastFoundAt(),
                entity.getResolvedAt(),
                entity.getResolvedBy(),
                entity.getResolveNote());
    }

    private static Map<String, Object> parseEvidence(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
