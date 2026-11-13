package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.rule.impl.MissingAnesthesiaOnArchiveRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1 归档缺麻醉规则测试。
 *
 * @author zhengyanchuang
 */
class MissingAnesthesiaOnArchiveRuleTest {

    private final MissingAnesthesiaOnArchiveRule rule = new MissingAnesthesiaOnArchiveRule();

    @Test
    void archivedCaseWithAnesthesiaHasNoFinding() {
        assertThat(rule.evaluate(QcCaseFixtures.builder().build())).isEmpty();
    }

    @Test
    void detectsArchivedCaseWithoutAnesthesia() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .anesthesia(null)
                .build())).hasSize(1);
    }

    @Test
    void ignoresInProgressCase() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(1)
                .anesthesia(null)
                .build())).isEmpty();
    }
}
