package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.rule.impl.AbnormalTerminationRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R4 异常终止规则测试。
 *
 * @author zhengyanchuang
 */
class AbnormalTerminationRuleTest {

    private final AbnormalTerminationRule rule = new AbnormalTerminationRule();

    @Test
    void detectsAbnormalTermination() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(3)
                .stage("SETUP")
                .build())).hasSize(1);
    }

    @Test
    void ignoresOtherStatuses() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(4)
                .build())).isEmpty();
    }
}
