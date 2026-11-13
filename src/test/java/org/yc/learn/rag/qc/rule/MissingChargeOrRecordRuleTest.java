package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.rule.impl.MissingChargeOrRecordRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R8 收费单/病历缺失规则测试。
 *
 * @author zhengyanchuang
 */
class MissingChargeOrRecordRuleTest {

    private final MissingChargeOrRecordRule rule = new MissingChargeOrRecordRule();

    @Test
    void completeCaseHasNoFinding() {
        assertThat(rule.evaluate(QcCaseFixtures.builder().build())).isEmpty();
    }

    @Test
    void detectsMissingChargeOrder() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .chargeOrderId(null)
                .build())).hasSize(1);
    }

    @Test
    void detectsMissingMedicalRecordNumber() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .dzblNo(" ")
                .build())).hasSize(1);
    }

    @Test
    void ignoresNotStartedCase() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(0)
                .chargeOrderId(null)
                .dzblNo(null)
                .caseId(null)
                .build())).isEmpty();
    }
}
