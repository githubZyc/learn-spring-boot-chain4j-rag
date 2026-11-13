package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.List;
import java.util.Map;

/**
 * R4：异常终止手术，需要复盘。
 *
 * @author zhengyanchuang
 */
@Component
public class AbnormalTerminationRule implements QcRule {

    public static final String CODE = "R4_ABNORMAL_TERMINATION";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "异常终止";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.WARNING;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        if (!qcCase.abnormalTerminated()) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "异常终止手术，需要复盘",
                "质控记录 " + qcCase.recordNo() + " 状态为异常终止，当前阶段 "
                        + (qcCase.currentStage() == null ? "" : qcCase.currentStage())
                        + "，请补充复盘结论。",
                Map.of("qcStatus", qcCase.qcStatus(),
                        "currentStage", qcCase.currentStage() == null ? "" : qcCase.currentStage())));
    }
}
