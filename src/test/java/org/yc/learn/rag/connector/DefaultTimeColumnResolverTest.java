package org.yc.learn.rag.connector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认时间字段推断的单元测试。
 *
 * @author zhengyanchuang
 */
class DefaultTimeColumnResolverTest {

    @Test
    void prefersCreateTimeOverUpdateTime() {
        List<SchemaColumn> columns = List.of(
                column("id", "bigint"),
                column("update_time", "datetime"),
                column("create_time", "datetime"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isEqualTo("create_time");
    }

    @Test
    void fallsBackToUpdateTime() {
        List<SchemaColumn> columns = List.of(
                column("id", "bigint"),
                column("update_time", "datetime"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isEqualTo("update_time");
    }

    @Test
    void recognizesCreatedOnStyleColumns() {
        List<SchemaColumn> columns = List.of(
                column("autoId", "int"),
                column("modifiedOn", "datetime"),
                column("createdOn", "datetime"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isEqualTo("createdOn");
    }

    @Test
    void fallsBackToTemporalColumnWithDateInName() {
        List<SchemaColumn> columns = List.of(
                column("id", "bigint"),
                column("biz_date", "date"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isEqualTo("biz_date");
    }

    @Test
    void returnsNullWhenNoTemporalColumnExists() {
        List<SchemaColumn> columns = List.of(
                column("id", "bigint"),
                column("amount", "decimal"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isNull();
    }

    @Test
    void preservesOriginalColumnCase() {
        List<SchemaColumn> columns = List.of(column("Create_Time", "datetime"));

        assertThat(DefaultTimeColumnResolver.resolve(columns)).isEqualTo("Create_Time");
    }

    private static SchemaColumn column(String name, String type) {
        return new SchemaColumn(name, type, true, false, "", "");
    }
}
