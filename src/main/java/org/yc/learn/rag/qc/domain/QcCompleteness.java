package org.yc.learn.rag.qc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 质控数据完整性判断，规则与病例详情共用，避免口径不一致。
 *
 * @author zhengyanchuang
 */
public final class QcCompleteness {

    private QcCompleteness() {
    }

    /**
     * 当前阶段应收但缺失的数据项。
     */
    public static List<String> missingStages(QcCase qcCase) {
        if (!qcCase.shouldHaveData()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        if (qcCase.setup() == null) {
            missing.add("开台配置");
        }
        if (qcCase.stageAtLeast("ANESTHESIA") && qcCase.anesthesia() == null) {
            missing.add("麻醉");
        }
        if (qcCase.stageAtLeast("EXTRACTION") && qcCase.extraction() == null) {
            missing.add("取发");
        }
        if (qcCase.stageAtLeast("PLANTING") && qcCase.planting() == null) {
            missing.add("种植");
        }
        return missing;
    }
}
