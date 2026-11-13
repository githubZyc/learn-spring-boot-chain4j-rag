package org.yc.learn.rag.qc.rule;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.qc.rule.impl.ArchiveTimeoutRule;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R7 归档超时规则测试。
 *
 * @author zhengyanchuang
 */
class ArchiveTimeoutRuleTest {

    private final ArchiveTimeoutRule rule =
            new ArchiveTimeoutRule(QcCaseFixtures.properties(7, 180, 0.10));

    @Test
    void onTimeArchiveHasNoFinding() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .filedTime(LocalDateTime.of(2026, 11, 5, 10, 0))
                .build())).isEmpty();
    }

    @Test
    void detectsLateArchive() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .filedTime(LocalDateTime.of(2026, 11, 12, 10, 0))
                .build())).hasSize(1);
    }

    @Test
    void detectsMissingFiledTime() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .filedTime(null)
                .build())).hasSize(1);
    }

    @Test
    void ignoresNotArchivedCase() {
        assertThat(rule.evaluate(QcCaseFixtures.builder()
                .status(1)
                .filedTime(LocalDateTime.of(2026, 11, 20, 10, 0))
                .build())).isEmpty();
    }
}
