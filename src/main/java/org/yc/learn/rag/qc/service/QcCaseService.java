package org.yc.learn.rag.qc.service;

import org.springframework.stereotype.Service;
import org.yc.learn.rag.qc.data.QcCaseLoader;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.domain.QcCompleteness;
import org.yc.learn.rag.qc.dto.QcCaseDetailDto;
import org.yc.learn.rag.qc.dto.QcFindingDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 病例 360：主记录、阶段时间线、异常、缺失项。
 *
 * @author zhengyanchuang
 */
@Service
public class QcCaseService {

    private final QcCaseLoader caseLoader;
    private final QcFindingService findingService;

    public QcCaseService(QcCaseLoader caseLoader, QcFindingService findingService) {
        this.caseLoader = caseLoader;
        this.findingService = findingService;
    }

    public QcCaseDetailDto detail(long recordId) {
        QcCase qcCase = caseLoader.loadById(recordId);
        List<QcFindingDto> findings = findingService.findingsForRecord(recordId);
        List<QcCaseDetailDto.StageDto> stages = buildStages(qcCase);
        List<String> missingStages = QcCompleteness.missingStages(qcCase);
        return new QcCaseDetailDto(qcCase, findings, stages, missingStages);
    }

    private List<QcCaseDetailDto.StageDto> buildStages(QcCase qcCase) {
        List<QcCaseDetailDto.StageDto> stages = new ArrayList<>();
        stages.add(stage("SETUP", "开台", qcCase, null, null));
        stages.add(stage("ANESTHESIA", "麻醉", qcCase,
                qcCase.anesthesia() == null ? null : qcCase.anesthesia().startTime(),
                qcCase.anesthesia() == null ? null : qcCase.anesthesia().endTime()));
        stages.add(stage("EXTRACTION", "取发", qcCase,
                qcCase.extraction() == null ? null : qcCase.extraction().extractionStartTime(),
                qcCase.extraction() == null ? null : qcCase.extraction().extractionEndTime()));
        stages.add(stage("PLANTING", "种植", qcCase,
                qcCase.planting() == null ? null : qcCase.planting().startTime(),
                qcCase.planting() == null ? null : qcCase.planting().endTime()));
        stages.add(stage("FINISHED", "手术结束", qcCase,
                qcCase.surgeryStartTime(), qcCase.surgeryEndTime()));
        return stages;
    }

    private QcCaseDetailDto.StageDto stage(String code, String name, QcCase qcCase,
                                           LocalDateTime start, LocalDateTime end) {
        Long duration = start == null || end == null
                ? null
                : Duration.between(start, end).toMinutes();
        return new QcCaseDetailDto.StageDto(code, name, stageStatus(qcCase, code), start, end, duration);
    }

    private String stageStatus(QcCase qcCase, String code) {
        if ("FINISHED".equals(code) && (qcCase.archived() || qcCase.qcStatus() == 2)) {
            return "DONE";
        }
        int current = QcCase.stageIndex(qcCase.currentStage());
        int target = QcCase.stageIndex(code);
        if (target <= 0 || current <= 0) {
            return "PENDING";
        }
        if (isDataMissing(qcCase, code) && target <= current) {
            return "MISSING";
        }
        if (target < current) {
            return "DONE";
        }
        if (target == current) {
            return "IN_PROGRESS";
        }
        return "PENDING";
    }

    private boolean isDataMissing(QcCase qcCase, String code) {
        return switch (code) {
            case "SETUP" -> qcCase.setup() == null;
            case "ANESTHESIA" -> qcCase.anesthesia() == null;
            case "EXTRACTION" -> qcCase.extraction() == null;
            case "PLANTING" -> qcCase.planting() == null;
            default -> false;
        };
    }
}
