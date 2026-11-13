package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.List;
import java.util.Map;

/**
 * R1：已归档但缺少麻醉数据。
 *
 * @author zhengyanchuang
 */
@Component
public class MissingAnesthesiaOnArchiveRule implements QcRule {

    public static final String CODE = "R1_ARCHIVED_MISSING_ANESTHESIA";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "归档缺麻醉";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.CRITICAL;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        if (!qcCase.archived() || qcCase.anesthesia() != null) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "已归档但缺少麻醉数据",
                "质控记录 " + qcCase.recordNo() + " 已归档，但麻醉子表没有记录，归档材料不完整。",
                Map.of("qcStatus", qcCase.qcStatus(), "missing", "anesthesia")));
    }
}
