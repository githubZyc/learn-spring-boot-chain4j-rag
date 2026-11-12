package org.yc.learn.rag.connector.mysql;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.connector.ConnectorConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema 提取选项解析的单元测试。
 *
 * @author zhengyanchuang
 */
class SchemaExtractOptionsTest {

    @Test
    void parsesDatabaseFromUrlIgnoringQuery() {
        assertThat(SchemaExtractOptions.parseDatabase(
                "jdbc:mysql://localhost:3306/his?useSSL=false&serverTimezone=Asia/Shanghai"))
                .isEqualTo("his");
    }

    @Test
    void usesUrlDatabaseWhenNotConfiguredExplicitly() {
        SchemaExtractOptions options = SchemaExtractOptions.from(new ConnectorConfig(Map.of(
                "url", "jdbc:mysql://localhost:3306/his",
                "username", "reader")));

        assertThat(options.database()).isEqualTo("his");
        assertThat(options.tableWhitelist()).isEmpty();
        assertThat(options.extraSensitiveColumns()).isEmpty();
        assertThat(options.defaultTimeColumn()).isNull();
    }

    @Test
    void explicitDatabaseOverridesUrl() {
        SchemaExtractOptions options = SchemaExtractOptions.from(new ConnectorConfig(Map.of(
                "url", "jdbc:mysql://localhost:3306/shop",
                "username", "reader",
                "database", "his")));

        assertThat(options.database()).isEqualTo("his");
    }

    @Test
    void parsesWhitelistAndExtraSensitiveColumns() {
        SchemaExtractOptions options = SchemaExtractOptions.from(new ConnectorConfig(Map.of(
                "url", "jdbc:mysql://localhost:3306/his",
                "username", "reader",
                "tableWhitelist", "e__dzbl, e__brxx",
                "extraSensitiveColumns", "blnr, zymc",
                "defaultTimeColumn", "create_time")));

        assertThat(options.tableWhitelist()).containsExactlyInAnyOrder("e__dzbl", "e__brxx");
        assertThat(options.extraSensitiveColumns()).containsExactlyInAnyOrder("blnr", "zymc");
        assertThat(options.defaultTimeColumn()).isEqualTo("create_time");
    }

    @Test
    void parsesTableAliases() {
        SchemaExtractOptions options = SchemaExtractOptions.from(new ConnectorConfig(Map.of(
                "url", "jdbc:mysql://localhost:3306/his",
                "username", "reader",
                "tableAliases", "e__dzbl=电子病历表, e__dzblmx=电子病历明细表")));

        assertThat(options.tableAliases())
                .containsEntry("e__dzbl", "电子病历表")
                .containsEntry("e__dzblmx", "电子病历明细表");
    }

    @Test
    void rejectsMalformedTableAlias() {
        assertThatThrownBy(() -> SchemaExtractOptions.from(new ConnectorConfig(Map.of(
                "url", "jdbc:mysql://localhost:3306/his",
                "username", "reader",
                "tableAliases", "e__dzbl"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tableAliases");
    }

    @Test
    void rejectsUrlWithoutDatabase() {
        assertThatThrownBy(() -> SchemaExtractOptions.parseDatabase("jdbc:mysql://localhost:3306"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少数据库名");
    }

    @Test
    void rejectsNonMySqlUrl() {
        assertThatThrownBy(() -> SchemaExtractOptions.parseDatabase("jdbc:postgresql://localhost/his"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:mysql://");
    }
}
