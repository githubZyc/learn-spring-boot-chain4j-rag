package org.yc.learn.rag.connector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Schema 文档渲染的单元测试：确保表名、中文注释、字段注释都进入 embedding 文本。
 *
 * @author zhengyanchuang
 */
class SchemaDocumentTest {

    @Test
    void rendersTableMetadataColumnsIndexesAndSensitiveFields() {
        SchemaDocument document = new SchemaDocument(
                "his",
                "e__dzbl",
                "电子病历表",
                "id",
                List.of(
                        new SchemaColumn("id", "bigint", false, true, "病历ID", ""),
                        new SchemaColumn("patient_name", "varchar(32)", true, false, "患者姓名", ""),
                        new SchemaColumn("create_time", "datetime", false, false, "创建时间", "")),
                List.of(new SchemaIndex("idx_create_time", false, List.of("create_time"))),
                "create_time",
                List.of("patient_name"));

        String text = document.toEmbeddingText();

        assertThat(text)
                .contains("表名: e__dzbl")
                .contains("中文名: 电子病历表")
                .contains("数据库: his")
                .contains("主键: id")
                .contains("默认时间字段: create_time")
                .contains("- id bigint 主键，病历ID")
                .contains("- patient_name varchar(32)，患者姓名")
                .contains("索引: idx_create_time(create_time)")
                .contains("敏感字段: patient_name");
    }

    @Test
    void omitsBlankOptionalSections() {
        SchemaDocument document = new SchemaDocument(
                "his",
                "t_demo",
                null,
                null,
                List.of(new SchemaColumn("id", "bigint", false, false, null, null)),
                List.of(),
                null,
                List.of());

        String text = document.toEmbeddingText();

        assertThat(text)
                .contains("表名: t_demo")
                .contains("字段:\n- id bigint\n")
                .doesNotContain("中文名:")
                .doesNotContain("主键:")
                .doesNotContain("索引:")
                .doesNotContain("敏感字段:");
    }
}
