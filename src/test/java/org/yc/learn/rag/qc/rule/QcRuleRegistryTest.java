package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.domain.QcCase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 质控规则注册表测试。
 *
 * @author zhengyanchuang
 */
class QcRuleRegistryTest {

    @Test
    void registersAndSortsRulesByCode() {
        QcRuleRegistry registry = new QcRuleRegistry(List.of(
                new StubRule("R2"), new StubRule("R1")));

        assertThat(registry.rules()).extracting(QcRule::code).containsExactly("R1", "R2");
        assertThat(registry.get("R1").code()).isEqualTo("R1");
    }

    @Test
    void rejectsDuplicateCode() {
        assertThatThrownBy(() -> new QcRuleRegistry(List.of(
                new StubRule("R1"), new StubRule("R1"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    void rejectsUnknownCode() {
        QcRuleRegistry registry = new QcRuleRegistry(List.of(new StubRule("R1")));

        assertThatThrownBy(() -> registry.get("R9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知");
    }

    private record StubRule(String code) implements QcRule {

        @Override
        public String name() {
            return code;
        }

        @Override
        public QcSeverity severity() {
            return QcSeverity.INFO;
        }

        @Override
        public List<QcFindingDraft> evaluate(QcCase qcCase) {
            return List.of();
        }
    }
}
