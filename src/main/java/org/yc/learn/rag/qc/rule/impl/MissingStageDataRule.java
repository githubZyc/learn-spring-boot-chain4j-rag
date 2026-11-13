package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.domain.QcCompleteness;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.List;
import java.util.Map;

/**
 * R2：当前阶段已达到，但对应阶段数据缺失。
 * <p>
 * 必须按阶段判断：还在开台的病例不应被要求有种植数据，否则会产生大量误报。
 *
 * @author zhengyanchuang
 */
@Component
public class MissingStageDataRule implements QcRule {

    public static final String CODE = "R2_MISSING_STAGE_DATA";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "应收阶段缺项";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.CRITICAL;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        List<String> missing = QcCompleteness.missingStages(qcCase);
        if (missing.isEmpty()) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "阶段已到 " + safe(qcCase.currentStage()) + "，缺少" + String.join("、", missing),
                "质控记录 " + qcCase.recordNo() + " 当前阶段为 " + safe(qcCase.currentStage())
                        + "，应收阶段数据缺失：" + String.join("、", missing) + "。",
                Map.of("currentStage", safe(qcCase.currentStage()), "missing", missing)));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
