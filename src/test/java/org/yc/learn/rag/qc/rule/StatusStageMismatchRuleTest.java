package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.rule.impl.StatusStageMismatchRule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3 状态阶段一致性测试。
 *
 * @author zhengyanchuang
 */
class StatusStageMismatchRuleTest {

    private final StatusStageMismatchRule rule = new StatusStageMismatchRule();

    @Test
    void archivedFinishedCaseIsConsistent() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(4).stage("FINISHED").build())).isEmpty();
    }

    @Test
    void detectsInProgressButFinishedStage() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(1).stage("FINISHED").build())).hasSize(1);
    }

    @Test
    void detectsCompletedButNotFinishedStage() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(2).stage("PLANTING").build())).hasSize(1);
    }

    @Test
    void detectsArchivedButNotFinishedStage() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(4).stage("EXTRACTION").build())).hasSize(1);
    }

    @Test
    void doesNotFlagAbnormalTermination() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(3).stage("SETUP").build())).isEmpty();
    }
}
