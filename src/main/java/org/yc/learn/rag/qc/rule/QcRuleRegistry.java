package org.yc.learn.rag.qc.rule;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控规则注册表；规则编码重复时启动失败，避免静默覆盖。
 *
 * @author zhengyanchuang
 */
@Component
public class QcRuleRegistry {

    private final Map<String, QcRule> rules;

    public QcRuleRegistry(List<QcRule> rules) {
        Map<String, QcRule> byCode = new HashMap<>();
        for (QcRule rule : rules) {
            QcRule previous = byCode.put(rule.code(), rule);
            if (previous != null) {
                throw new IllegalStateException("质控规则编码重复：" + rule.code());
            }
        }
        this.rules = Map.copyOf(byCode);
    }

    public List<QcRule> rules() {
        return rules.values().stream()
                .sorted(Comparator.comparing(QcRule::code))
                .toList();
    }

    public QcRule get(String code) {
        QcRule rule = rules.get(code);
        if (rule == null) {
            throw new IllegalArgumentException("未知的质控规则：" + code);
        }
        return rule;
    }
}
