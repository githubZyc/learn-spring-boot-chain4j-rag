package org.yc.learn.rag.qc.rule.impl;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * R7：已归档但缺少归档时间，或归档超过时限。
 *
 * @author zhengyanchuang
 */
@Component
public class ArchiveTimeoutRule implements QcRule {

    public static final String CODE = "R7_ARCHIVE_TIMEOUT";
    private static final int DEFAULT_ARCHIVE_DAYS = 7;

    private final AppProperties properties;

    public ArchiveTimeoutRule(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "归档超时";
    }

    @Override
    public QcSeverity severity() {
        return QcSeverity.WARNING;
    }

    @Override
    public List<QcFindingDraft> evaluate(QcCase qcCase) {
        if (!qcCase.archived()) {
            return List.of();
        }
        if (qcCase.filedTime() == null) {
            return List.of(QcFindingDraft.of(this, qcCase,
                    "已归档但缺少归档时间",
                    "质控记录 " + qcCase.recordNo() + " 状态为已归档，但 filed_time 为空。",
                    Map.of("qcStatus", qcCase.qcStatus())));
        }
        if (qcCase.surgeryDate() == null) {
            return List.of();
        }
        long days = ChronoUnit.DAYS.between(qcCase.surgeryDate(), qcCase.filedTime().toLocalDate());
        int threshold = archiveDays();
        if (days <= threshold) {
            return List.of();
        }
        return List.of(QcFindingDraft.of(this, qcCase,
                "归档超时 " + days + " 天",
                "质控记录 " + qcCase.recordNo() + " 手术日期 " + qcCase.surgeryDate()
                        + "，归档日期 " + qcCase.filedTime().toLocalDate()
                        + "，超过 " + threshold + " 天时限。",
                Map.of("archiveDays", days, "thresholdDays", threshold)));
    }

    private int archiveDays() {
        AppProperties.Qc qc = properties.qc();
        return qc == null || qc.archiveDays() <= 0 ? DEFAULT_ARCHIVE_DAYS : qc.archiveDays();
    }
}
