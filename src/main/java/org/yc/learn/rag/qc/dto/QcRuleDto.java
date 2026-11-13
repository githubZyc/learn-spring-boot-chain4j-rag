package org.yc.learn.rag.qc.dto;

import org.yc.learn.rag.qc.rule.QcSeverity;

/**
 * 规则列表项。
 *
 * @author zhengyanchuang
 */
public record QcRuleDto(String code, String name, QcSeverity severity, boolean enabled) {
}
