package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * R8：应收数病例缺少收费单、电子病历或病历编号。
 *
 * @author zhengyanchuang
 */
@Component
public class MissingChargeOrRecordRule implements QcRule {

    public static final String CODE = "R8_MISSING_CHARGE_OR_RECORD";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "收费单/病历缺失";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.CRITICAL;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        if (!qcCase.shouldHaveData()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(qcCase.chargeOrderId())) {
            missing.add("收费单");
        }
        if (isBlank(qcCase.dzblNo())) {
            missing.add("电子病历");
        }
        if (isBlank(qcCase.caseId())) {
            missing.add("病历编号");
        }
        if (missing.isEmpty()) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "缺少" + String.join("、", missing),
                "质控记录 " + qcCase.recordNo() + " 缺少：" + String.join("、", missing) + "。",
                Map.of("missing", missing)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
