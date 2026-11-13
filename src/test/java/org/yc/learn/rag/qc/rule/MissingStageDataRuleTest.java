package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.rule.impl.MissingStageDataRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2 阶段缺项规则测试。
 *
 * @author zhengyanchuang
 */
class MissingStageDataRuleTest {

    private final MissingStageDataRule rule = new MissingStageDataRule();

    @Test
    void healthyFinishedCaseHasNoFinding() {
        assertThat(rule.evaluate(QcCaseFixtures.builder().build())).isEmpty();
    }

    @Test
    void detectsMissingAnesthesiaWhenStageReached() {
        QcCase qcCase = QcCaseFixtures.builder()
                .stage("EXTRACTION")
                .anesthesia(null)
                .build();

        var findings = rule.evaluate(qcCase);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).evidence().get("missing").toString()).contains("麻醉");
    }

    @Test
    void doesNotRequireLaterStageDataWhenStillAtSetup() {
        QcCase qcCase = QcCaseFixtures.builder()
                .status(0)
                .stage("SETUP")
                .setup(null)
                .anesthesia(null)
                .extraction(null)
                .planting(null)
                .build();

        assertThat(rule.evaluate(qcCase)).isEmpty();
    }

    @Test
    void reportsAllMissingStagesTogether() {
        QcCase qcCase = QcCaseFixtures.builder()
                .stage("FINISHED")
                .anesthesia(null)
                .extraction(null)
                .planting(null)
                .build();

        var findings = rule.evaluate(qcCase);

        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).evidence().get("missing").toString())
                .contains("麻醉", "取发", "种植");
    }
}
