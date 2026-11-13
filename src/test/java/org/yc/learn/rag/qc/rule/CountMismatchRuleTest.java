package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.impl.CountMismatchRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6 取发/种植数量规则测试。
 *
 * @author zhengyanchuang
 */
class CountMismatchRuleTest {

    private final CountMismatchRule rule =
            new CountMismatchRule(QcCaseFixtures.properties(7, 180, 0.10));

    @Test
    void similarCountsHaveNoFinding() {
        QcCase qcCase = QcCaseFixtures.builder()
                .extraction(QcCaseFixtures.extraction(1000, 500))
                .planting(QcCaseFixtures.planting(900, 500))
                .build();

        assertThat(rule.evaluate(qcCase)).isEmpty();
    }

    @Test
    void detectsLargeDifference() {
        QcCase qcCase = QcCaseFixtures.builder()
                .extraction(QcCaseFixtures.extraction(1000, 500))
                .planting(QcCaseFixtures.planting(700, 400))
                .build();

        assertThat(rule.evaluate(qcCase)).hasSize(1);
    }

    @Test
    void detectsLongHairTechniqueWithoutLongHairCounts() {
        QcCase qcCase = QcCaseFixtures.builder()
                .technique("2")
                .extraction(QcCaseFixtures.longHairExtraction())
                .planting(QcCaseFixtures.planting(1200, 0))
                .build();

        assertThat(rule.evaluate(qcCase)).hasSize(1);
    }
}
