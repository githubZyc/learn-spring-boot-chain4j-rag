package org.yc.learn.rag.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NL2SQL 安全护栏的单元测试。
 *
 * @author zhengyanchuang
 */
class SqlValidatorTest {

    @Test
    void acceptsSelectAndForcesOuterLimit() {
        SqlValidator.ValidatedSql validated = SqlValidator.validateAndNormalize(
                "SELECT id, createdOn FROM e__dzbl WHERE createdOn >= CURDATE()", "e__dzbl");

        assertThat(validated.displaySql())
                .isEqualTo("SELECT id, createdOn FROM e__dzbl WHERE createdOn >= CURDATE()");
        assertThat(validated.executableSql())
                .startsWith("SELECT * FROM (")
                .endsWith("LIMIT 200");
    }

    @Test
    void stripsMarkdownCodeFence() {
        SqlValidator.ValidatedSql validated = SqlValidator.validateAndNormalize(
                "```sql\nSELECT * FROM e__dzbl\n```", "e__dzbl");

        assertThat(validated.displaySql()).isEqualTo("SELECT * FROM e__dzbl");
    }

    @Test
    void allowsQualifiedAndBacktickedTableNames() {
        assertThat(SqlValidator.validateAndNormalize(
                "SELECT * FROM `his`.`e__dzbl`", "e__dzbl").displaySql())
                .isEqualTo("SELECT * FROM `his`.`e__dzbl`");
    }

    @Test
    void rejectsDmlAndDdl() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "DELETE FROM e__dzbl", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许 SELECT");

        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "DROP TABLE e__dzbl", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许 SELECT");
    }

    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "SELECT * FROM e__dzbl; DROP TABLE e__dzbl", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单条");
    }

    @Test
    void rejectsOtherTables() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "SELECT * FROM e__dzblmx", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许查询已授权的表");
    }

    @Test
    void allowsMultiTableJoinWithinAllowedSet() {
        SqlValidator.ValidatedSql validated = SqlValidator.validateAndNormalize(
                "SELECT a.id, b.record_id FROM e__dzbl a "
                        + "JOIN hospital_surgical_qc_record b ON b.id = a.record_id",
                Set.of("e__dzbl", "hospital_surgical_qc_record"));

        assertThat(validated.executableSql()).startsWith("SELECT * FROM (").endsWith("LIMIT 200");
        assertThat(validated.displaySql()).contains("JOIN hospital_surgical_qc_record");
    }

    @Test
    void inspectsAllReferencedTables() {
        SqlValidator.InspectedSql inspected = SqlValidator.inspect(
                "SELECT * FROM a JOIN b ON a.id = b.id JOIN c ON c.id = a.id");

        assertThat(inspected.tables()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void rejectsCommaJoin() {
        assertThatThrownBy(() -> SqlValidator.inspect(
                "SELECT * FROM e__dzbl a, hospital_surgical_qc_record b WHERE a.id = b.id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JOIN");
    }

    @Test
    void rejectsComments() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "SELECT * FROM e__dzbl -- 注释", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("注释");
    }

    @Test
    void rejectsLockingRead() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize(
                "SELECT * FROM e__dzbl FOR UPDATE", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止的关键字");
    }

    @Test
    void rejectsSqlWithoutTable() {
        assertThatThrownBy(() -> SqlValidator.validateAndNormalize("SELECT 1", "e__dzbl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有识别到目标表");
    }
}
