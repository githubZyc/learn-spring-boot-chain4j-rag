package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.domain.QcAnesthesia;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.impl.AnesthesiaDurationRule;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R5 麻醉时长规则测试。
 *
 * @author zhengyanchuang
 */
class AnesthesiaDurationRuleTest {

    private final AnesthesiaDurationRule rule =
            new AnesthesiaDurationRule(QcCaseFixtures.properties(7, 180, 0.10));

    @Test
    void normalDurationHasNoFinding() {
        assertThat(rule.evaluate(QcCaseFixtures.builder().build())).isEmpty();
    }

    @Test
    void detectsDurationOverThreshold() {
        QcCase qcCase = QcCaseFixtures.builder()
                .anesthesia(QcCaseFixtures.anesthesia(200))
                .build();

        assertThat(rule.evaluate(qcCase)).hasSize(1);
    }

    @Test
    void detectsNonPositiveDuration() {
        QcCase qcCase = QcCaseFixtures.builder()
                .anesthesia(QcCaseFixtures.anesthesia(0))
                .build();

        assertThat(rule.evaluate(qcCase)).hasSize(1);
    }

    @Test
    void computesDurationFromStartAndEndWhenMissing() {
        QcAnesthesia longCase = new QcAnesthesia(
                LocalDateTime.of(2026, 11, 1, 9, 0),
                LocalDateTime.of(2026, 11, 1, 12, 20),
                null, "", "", "");
        QcCase qcCase = QcCaseFixtures.builder().anesthesia(longCase).build();

        assertThat(rule.evaluate(qcCase)).hasSize(1);
    }
}
