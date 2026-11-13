package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.domain.QcAnesthesia;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * R5：麻醉时长异常（超过阈值或小于等于 0）。
 *
 * @author zhengyanchuang
 */
@Component
public class AnesthesiaDurationRule implements QcRule {

    public static final String CODE = "R5_ANESTHESIA_DURATION";
    private static final int DEFAULT_MAX_MINUTES = 180;

    private final AppProperties properties;

    public AnesthesiaDurationRule(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "麻醉时长异常";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.WARNING;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        QcAnesthesia anesthesia = qcCase.anesthesia();
        if (anesthesia == null) {
            return List.of();
        }
        Integer minutes = anesthesia.durationMinutes();
        if (minutes == null && anesthesia.startTime() != null && anesthesia.endTime() != null) {
            minutes = (int) Duration.between(anesthesia.startTime(), anesthesia.endTime()).toMinutes();
        }
        if (minutes == null) {
            return List.of();
        }
        int maxMinutes = maxMinutes();
        if (minutes > 0 && minutes <= maxMinutes) {
            return List.of();
        }
        String reason = minutes <= 0
                ? "麻醉时长小于等于 0，数据异常"
                : "麻醉时长 " + minutes + " 分钟，超过阈值 " + maxMinutes + " 分钟";
        return List.of(QcFindingDraft.of(this, qcCase,
                "麻醉时长异常",
                "质控记录 " + qcCase.recordNo() + "：" + reason + "。",
                Map.of("durationMinutes", minutes, "thresholdMinutes", maxMinutes)));
    }

    private int maxMinutes() {
        AppProperties.Qc qc = properties.qc();
        return qc == null || qc.anesthesiaMaxMin() <= 0 ? DEFAULT_MAX_MINUTES : qc.anesthesiaMaxMin();
    }
}
