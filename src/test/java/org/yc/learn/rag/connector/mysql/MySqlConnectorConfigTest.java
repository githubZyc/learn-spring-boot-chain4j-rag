package org.yc.learn.rag.connector.mysql;

import org.junit.jupiter.api.Test;
import org.yc.learn.rag.connector.ConnectorConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL 配置解析与标识符白名单校验的纯逻辑测试。
 *
 * @author zhengyanchuang
 */
class MySqlConnectorConfigTest {

    @Test
    void parsesDefaultsWhenCursorAndPageSizeMissing() {
        MySqlConnectorConfig config = MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "id,title,amount")));

        assertThat(config.cursorColumn()).isEqualTo("id");
        assertThat(config.pageSize()).isEqualTo(200);
        assertThat(config.columns()).containsExactly("id", "title", "amount");
    }

    @Test
    void parsesExplicitCursorAndPageSize() {
        MySqlConnectorConfig config = MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "id,title,amount",
                "cursorColumn", "updated_at",
                "pageSize", "50")));

        assertThat(config.cursorColumn()).isEqualTo("updated_at");
        assertThat(config.pageSize()).isEqualTo(50);
    }

    @Test
    void appendsCursorAndIdColumnsWhenNotSelected() {
        MySqlConnectorConfig config = MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "title",
                "cursorColumn", "updated_at")));

        assertThat(config.columns()).containsExactly("title", "updated_at", "id");
    }

    @Test
    void rejectsNonMySqlUrl() {
        assertThatThrownBy(() -> MySqlConnectorConfig.from(new ConnectorConfig(Map.of(
                "url", "jdbc:postgresql://localhost/db",
                "username", "reader",
                "table", "orders",
                "idColumn", "id",
                "columns", "id,title"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdbc:mysql://");
    }

    @Test
    void rejectsSqlInjectionInTableName() {
        ConnectorConfig config = baseConfig(Map.of("columns", "id,title"));

        assertThatThrownBy(() -> MySqlConnectorConfig.from(new ConnectorConfig(Map.of(
                "url", config.require("url"),
                "username", config.require("username"),
                "table", "orders; DROP TABLE users",
                "idColumn", "id",
                "columns", "id,title"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法标识符");
    }

    @Test
    void rejectsInvalidColumnName() {
        assertThatThrownBy(() -> MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "id,title from users"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法标识符");
    }

    @Test
    void rejectsEmptyColumns() {
        assertThatThrownBy(() -> MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", " , "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要配置一个列");
    }

    @Test
    void rejectsPageSizeOutOfRange() {
        assertThatThrownBy(() -> MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "id,title",
                "pageSize", "5000"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
    }

    @Test
    void rejectsNonNumericPageSize() {
        assertThatThrownBy(() -> MySqlConnectorConfig.from(baseConfig(Map.of(
                "columns", "id,title",
                "pageSize", "abc"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize 必须是整数");
    }

    private static ConnectorConfig baseConfig(Map<String, String> extra) {
        Map<String, String> properties = new java.util.HashMap<>();
        properties.put("url", "jdbc:mysql://localhost:3306/shop");
        properties.put("username", "reader");
        properties.put("password", "secret");
        properties.put("table", "orders");
        properties.put("idColumn", "id");
        properties.putAll(extra);
        return new ConnectorConfig(properties);
    }
}
