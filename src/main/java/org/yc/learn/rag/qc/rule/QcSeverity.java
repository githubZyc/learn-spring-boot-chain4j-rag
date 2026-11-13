package org.yc.learn.rag.qc.rule;

/**
 * 质控异常严重级别。
 *
 * @author zhengyanchuang
 */
public enum QcSeverity {

    /** 必须立即处理，通常涉及材料缺失或合规风险。 */
    CRITICAL,

    /** 当日处理，影响质控质量或看板准确性。 */
    WARNING,

    /** 观察项，用于复盘或趋势分析。 */
    INFO
}
