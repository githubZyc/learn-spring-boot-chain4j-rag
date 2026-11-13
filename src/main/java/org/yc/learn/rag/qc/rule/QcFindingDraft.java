package org.yc.learn.rag.qc.rule;

import org.yc.learn.rag.qc.domain.QcCase;

import java.time.LocalDate;
import java.util.Map;

/**
 * 规则命中的异常草稿；持久化由 {@code QcFindingService} 负责。
 *
 * @author zhengyanchuang
 */
public record QcFindingDraft(
        String ruleCode,
        String ruleName,
        QcSeverity severity,
        long recordId,
        String recordNo,
        String departId,
        String departName,
        String doctorId,
        String doctorName,
        LocalDate surgeryDate,
        String title,
        String detail,
        Map<String, Object> evidence) {

    public QcFindingDraft {
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }

    public static QcFindingDraft of(QcRule rule, QcCase qcCase, String title, String detail,
                                    Map<String, Object> evidence) {
        return new QcFindingDraft(
                rule.code(),
                rule.name(),
                rule.severity(),
                qcCase.id(),
                qcCase.recordNo(),
                qcCase.departId(),
                qcCase.departName(),
                qcCase.operationId(),
                "",
                qcCase.surgeryDate(),
                title,
                detail,
                evidence);
    }
}
