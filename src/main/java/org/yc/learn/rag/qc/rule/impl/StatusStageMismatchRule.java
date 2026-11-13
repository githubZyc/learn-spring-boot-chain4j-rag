package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.List;
import java.util.Map;

/**
 * R3：质控状态与当前阶段不一致。
 * <p>
 * 典型场景：阶段已经 FINISHED，但状态仍是“进行中”，导致看板与交接失真。
 *
 * @author zhengyanchuang
 */
@Component
public class StatusStageMismatchRule implements QcRule {

    public static final String CODE = "R3_STATUS_STAGE_MISMATCH";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "状态阶段不一致";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.WARNING;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        String reason = mismatchReason(qcCase);
        if (reason == null) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "状态与阶段不一致",
                "质控记录 " + qcCase.recordNo() + " 状态=" + qcCase.qcStatus()
                        + "，阶段=" + (qcCase.currentStage() == null ? "" : qcCase.currentStage())
                        + "，" + reason + "。",
                Map.of("qcStatus", qcCase.qcStatus(),
                        "currentStage", qcCase.currentStage() == null ? "" : qcCase.currentStage())));
    }

    private static String mismatchReason(QcCase qcCase) {
        String stage = qcCase.currentStage();
        return switch (qcCase.qcStatus()) {
            case 0 -> "SETUP".equalsIgnoreCase(stage) ? null : "未开始状态却已进入其他阶段";
            case 1 -> "FINISHED".equalsIgnoreCase(stage) ? "进行中状态但阶段已结束" : null;
            case 2 -> "FINISHED".equalsIgnoreCase(stage) ? null : "已完成状态但阶段不是 FINISHED";
            case 4 -> "FINISHED".equalsIgnoreCase(stage) ? null : "已归档状态但阶段不是 FINISHED";
            default -> null;
        };
    }
}
