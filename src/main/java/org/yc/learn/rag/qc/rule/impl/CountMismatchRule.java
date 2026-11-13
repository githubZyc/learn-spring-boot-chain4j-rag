package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.domain.QcExtraction;
import org.yc.learn.rag.qc.domain.QcPlanting;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * R6：取发与种植数量差异过大，或长发植发缺少长发阶段数据。
 *
 * @author zhengyanchuang
 */
@Component
public class CountMismatchRule implements QcRule {

    public static final String CODE = "R6_COUNT_MISMATCH";
    private static final double DEFAULT_DIFF_RATIO = 0.10;

    private final AppProperties properties;

    public CountMismatchRule(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "取发种植数量异常";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.WARNING;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        QcExtraction extraction = qcCase.extraction();
        QcPlanting planting = qcCase.planting();
        if (extraction == null || planting == null) {
            return List.of();
        }

        List<String> issues = new ArrayList<>();
        long extracted = extraction.totalExtracted();
        long planted = planting.totalPlanted();
        double ratio = diffRatio();
        if (extracted > 0) {
            double diff = Math.abs(extracted - planted) / (double) extracted;
            if (diff > ratio) {
                issues.add("取发 " + extracted + " 与种植 " + planted
                        + " 差异 " + Math.round(diff * 100) + "%，超过 "
                        + Math.round(ratio * 100) + "% 阈值");
            }
        }
        if (qcCase.hasTechnique(2) && !extraction.extractionOnly()) {
            long longExtracted = intValue(extraction.longSeparationSingle())
                    + intValue(extraction.longSeparationMulti());
            long longPlanted = intValue(planting.longPlantingSingle())
                    + intValue(planting.longPlantingMulti());
            if (longExtracted == 0 || longPlanted == 0) {
                issues.add("长发植发但长发取发/种植数量缺失");
            }
        }
        if (issues.isEmpty()) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "取发/种植数量异常",
                "质控记录 " + qcCase.recordNo() + "：" + String.join("；", issues) + "。",
                Map.of("extracted", extracted, "planted", planted, "issues", issues)));
    }

    private double diffRatio() {
        AppProperties.Qc qc = properties.qc();
        return qc == null || qc.countDiffRatio() <= 0 ? DEFAULT_DIFF_RATIO : qc.countDiffRatio();
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
