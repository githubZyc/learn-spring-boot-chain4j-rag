package org.yc.learn.rag.qc.dto;

import org.yc.learn.rag.qc.domain.QcCase;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 病例 360：主记录、阶段时间线、异常列表、缺失阶段。
 *
 * @author zhengyanchuang
 */
public record QcCaseDetailDto(
        QcCase qcCase,
        List<QcFindingDto> findings,
        List<StageDto> stages,
        List<String> missingStages) {

    public QcCaseDetailDto {
        findings = findings == null ? List.of() : List.copyOf(findings);
        stages = stages == null ? List.of() : List.copyOf(stages);
        missingStages = missingStages == null ? List.of() : List.copyOf(missingStages);
    }

    /**
     * 阶段时间线。
     */
    public record StageDto(
            String code,
            String name,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            Long durationMinutes) {
    }
}
