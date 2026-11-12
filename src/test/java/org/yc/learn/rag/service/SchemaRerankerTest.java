package org.yc.learn.rag.service;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.dto.SchemaMatchDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主表优先重排的单元测试。
 *
 * @author zhengyanchuang
 */
class SchemaRerankerTest {

    @Test
    void prefersMainTableOverDetailAndSharedTablesForGenericQuestion() {
        List<SchemaMatchDto> matches = List.of(
                match("e__dzblmx", "电子病历明细表", 0.883),
                match("e__dzblaccess", "电子病历共享表", 0.880),
                match("e__dzbl", "电子病历表", 0.860));

        List<SchemaMatchDto> ranked = SchemaReranker.rerank("电子病历数据存在哪个表？", matches, 3);

        assertThat(ranked).extracting(SchemaMatchDto::tableName)
                .containsExactly("e__dzbl", "e__dzblmx", "e__dzblaccess");
        assertThat(ranked.get(1).rankReason()).contains("明细");
        assertThat(ranked.get(2).rankReason()).contains("共享");
    }

    @Test
    void doesNotPenalizeDetailTableWhenQuestionAsksForDetail() {
        List<SchemaMatchDto> matches = List.of(
                match("e__dzbl", "电子病历表", 0.860),
                match("e__dzblmx", "电子病历明细表", 0.883));

        List<SchemaMatchDto> ranked = SchemaReranker.rerank("电子病历明细表有哪些字段？", matches, 2);

        assertThat(ranked.get(0).tableName()).isEqualTo("e__dzblmx");
        assertThat(ranked.get(0).rankReason()).doesNotContain("明细");
    }

    @Test
    void penalizesBackupSnapshotTables() {
        List<SchemaMatchDto> matches = List.of(
                match("e__dzbl_bak", "电子病历表-旧备份", 0.900),
                match("e__dzbl", "电子病历表", 0.860));

        List<SchemaMatchDto> ranked = SchemaReranker.rerank("电子病历数据存在哪个表？", matches, 2);

        assertThat(ranked.get(0).tableName()).isEqualTo("e__dzbl");
        assertThat(ranked.get(1).rankReason()).contains("备份");
    }

    @Test
    void keepsVectorScoreAndExplainsRanking() {
        List<SchemaMatchDto> ranked = SchemaReranker.rerank(
                "电子病历", List.of(match("e__dzblmx", "电子病历明细表", 0.883)), 1);

        assertThat(ranked.get(0).vectorScore()).isEqualTo(0.883);
        assertThat(ranked.get(0).score()).isLessThan(0.883);
        assertThat(ranked.get(0).rankReason()).isNotBlank();
    }

    @Test
    void returnsEmptyForEmptyInput() {
        assertThat(SchemaReranker.rerank("随便问问", List.of(), 5)).isEmpty();
    }

    private static SchemaMatchDto match(String tableName, String comment, double score) {
        return new SchemaMatchDto(tableName, comment, "", List.of(), score, score, "", tableName);
    }
}
