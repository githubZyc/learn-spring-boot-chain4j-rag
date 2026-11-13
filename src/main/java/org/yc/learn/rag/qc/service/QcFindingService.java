package org.yc.learn.rag.qc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.exception.ApiException;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.domain.QcFindingEntity;
import org.yc.learn.rag.qc.domain.QcFindingStatus;
import org.yc.learn.rag.qc.dto.QcFindingDto;
import org.yc.learn.rag.qc.dto.QcPageDto;
import org.yc.learn.rag.qc.repository.QcFindingRepository;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 质控异常的持久化、查询与闭环操作。
 * <p>
 * 扫描采用 upsert：同一规则同一病例只保留一条记录；消失的异常自动关闭；
 * 人工标记“非问题”的异常在后续扫描中不会被重新打开。
 *
 * @author zhengyanchuang
 */
@Service
public class QcFindingService {

    private static final Set<QcFindingStatus> ACTIVE_STATUSES =
            Set.of(QcFindingStatus.OPEN, QcFindingStatus.ACK);
    private static final String SYSTEM_OPERATOR = "SYSTEM";

    private final QcFindingRepository repository;
    private final ObjectMapper objectMapper;

    public QcFindingService(QcFindingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用一次扫描结果。
     */
    public ScanApplyResult applyScan(List<QcCase> cases, List<QcFindingDraft> drafts) {
        List<Long> recordIds = cases.stream().map(QcCase::id).toList();
        Map<String, QcFindingEntity> existingByKey = new HashMap<>();
        if (!recordIds.isEmpty()) {
            for (QcFindingEntity entity : repository.findByRecordIdIn(recordIds)) {
                existingByKey.put(key(entity.getRuleCode(), entity.getRecordId()), entity);
            }
        }

        Set<String> activeKeys = drafts.stream()
                .map(draft -> key(draft.ruleCode(), draft.recordId()))
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        int created = 0;
        int updated = 0;
        int reopened = 0;
        int closed = 0;
        List<QcFindingEntity> toSave = new ArrayList<>();

        for (QcFindingDraft draft : drafts) {
            String draftKey = key(draft.ruleCode(), draft.recordId());
            QcFindingEntity entity = existingByKey.get(draftKey);
            if (entity == null) {
                entity = new QcFindingEntity();
                entity.setRuleCode(draft.ruleCode());
                entity.setRecordId(draft.recordId());
                entity.setStatus(QcFindingStatus.OPEN);
                entity.setFirstFoundAt(now);
                created++;
            } else {
                if (entity.getStatus() == QcFindingStatus.IGNORED) {
                    // 人工确认过非问题，尊重该判断，不重复打扰
                    continue;
                }
                if (entity.getStatus() == QcFindingStatus.RESOLVED) {
                    entity.setStatus(QcFindingStatus.OPEN);
                    entity.setResolvedAt(null);
                    entity.setResolvedBy(null);
                    entity.setResolveNote("扫描再次命中，自动重新打开");
                    reopened++;
                } else {
                    updated++;
                }
            }
            fill(entity, draft);
            entity.setLastFoundAt(now);
            toSave.add(entity);
        }

        for (QcFindingEntity entity : existingByKey.values()) {
            String existingKey = key(entity.getRuleCode(), entity.getRecordId());
            if (ACTIVE_STATUSES.contains(entity.getStatus()) && !activeKeys.contains(existingKey)) {
                entity.setStatus(QcFindingStatus.RESOLVED);
                entity.setResolvedAt(now);
                entity.setResolvedBy(SYSTEM_OPERATOR);
                entity.setResolveNote("扫描未再命中，自动关闭");
                closed++;
                toSave.add(entity);
            }
        }

        repository.saveAll(toSave);
        return new ScanApplyResult(created, updated, reopened, closed, drafts.size());
    }

    public List<QcFindingDto> findingsForRecord(long recordId) {
        return repository.findByRecordId(recordId).stream()
                .map(this::toDto)
                .toList();
    }

    public QcPageDto<QcFindingDto> list(QcFindingStatus status, QcSeverity severity, String ruleCode,
                                        String departId, LocalDate from, LocalDate to,
                                        int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Specification<QcFindingEntity> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (ruleCode != null && !ruleCode.isBlank()) {
                predicates.add(cb.equal(root.get("ruleCode"), ruleCode));
            }
            if (departId != null && !departId.isBlank()) {
                predicates.add(cb.equal(root.get("departId"), departId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("surgeryDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("surgeryDate"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<QcFindingEntity> result = repository.findAll(specification,
                PageRequest.of(safePage - 1, safeSize,
                        Sort.by(Sort.Direction.DESC, "lastFoundAt")));
        List<QcFindingDto> items = result.getContent().stream().map(this::toDto).toList();
        return new QcPageDto<>(items, result.getTotalElements(), safePage, safeSize);
    }

    public QcFindingDto ack(long id, String operator) {
        QcFindingEntity entity = get(id);
        entity.setStatus(QcFindingStatus.ACK);
        entity.setOwner(operator);
        return toDto(repository.save(entity));
    }

    public QcFindingDto resolve(long id, String operator, String note) {
        QcFindingEntity entity = get(id);
        entity.setStatus(QcFindingStatus.RESOLVED);
        entity.setResolvedAt(Instant.now());
        entity.setResolvedBy(operator);
        entity.setResolveNote(note);
        return toDto(repository.save(entity));
    }

    public QcFindingDto ignore(long id, String operator, String note) {
        QcFindingEntity entity = get(id);
        entity.setStatus(QcFindingStatus.IGNORED);
        entity.setResolvedAt(Instant.now());
        entity.setResolvedBy(operator);
        entity.setResolveNote(note);
        return toDto(repository.save(entity));
    }

    public long openCount() {
        return repository.countByStatusIn(ACTIVE_STATUSES);
    }

    public long criticalCount() {
        return repository.countBySeverityAndStatusIn(QcSeverity.CRITICAL, ACTIVE_STATUSES);
    }

    public List<QcFindingDto> topOpen() {
        return repository.findTop10ByStatusInOrderByLastFoundAtDesc(ACTIVE_STATUSES).stream()
                .map(this::toDto)
                .toList();
    }

    private QcFindingEntity get(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QC_FINDING_NOT_FOUND",
                        "质控异常不存在：" + id));
    }

    private void fill(QcFindingEntity entity, QcFindingDraft draft) {
        entity.setRuleName(draft.ruleName());
        entity.setSeverity(draft.severity());
        entity.setRecordNo(draft.recordNo());
        entity.setDepartId(draft.departId());
        entity.setDepartName(draft.departName());
        entity.setDoctorId(draft.doctorId());
        entity.setDoctorName(draft.doctorName());
        entity.setSurgeryDate(draft.surgeryDate());
        entity.setTitle(draft.title());
        entity.setDetail(draft.detail());
        entity.setEvidenceJson(toJson(draft.evidence()));
    }

    private QcFindingDto toDto(QcFindingEntity entity) {
        return QcFindingDto.from(entity, objectMapper);
    }

    private String toJson(Map<String, Object> evidence) {
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String key(String ruleCode, long recordId) {
        return ruleCode + "#" + recordId;
    }

    /**
     * 一次扫描的写入统计。
     */
    public record ScanApplyResult(int created, int updated, int reopened, int closed, int totalFindings) {
    }
}
