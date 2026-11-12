package org.yc.learn.rag.connector.mysql;

import org.yc.learn.rag.connector.ConnectorConfig;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * MySQL 连接器的强类型配置。
 * <p>
 * 表名、列名最终会拼进 SQL，因此这里用严格白名单校验（只允许字母、数字、下划线，
 * 表名允许一到两段 {@code schema.table}）。配置项来自数据源记录，不是用户提问，
 * 但仍然要走校验，避免配置被篡改后形成 SQL 注入。
 *
 * @author zhengyanchuang
 */
public record MySqlConnectorConfig(
        String url,
        String username,
        String password,
        String table,
        String idColumn,
        String cursorColumn,
        List<String> columns,
        int pageSize) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int MAX_TABLE_PARTS = 2;

    public MySqlConnectorConfig {
        if (url == null || !url.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("MySQL 连接地址必须以 jdbc:mysql:// 开头");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("缺少数据源配置项：username");
        }
        password = password == null ? "" : password;
        validateQualifiedIdentifier(table, "table");
        validateIdentifier(idColumn, "idColumn");
        validateIdentifier(cursorColumn, "cursorColumn");

        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("数据源至少需要配置一个列：columns");
        }
        Set<String> uniqueColumns = new LinkedHashSet<>();
        for (String column : columns) {
            validateIdentifier(column, "columns");
            uniqueColumns.add(column);
        }
        // 游标列与主键列必须出现在查询结果里，否则无法翻页、无法生成稳定文档 ID
        uniqueColumns.add(cursorColumn);
        uniqueColumns.add(idColumn);
        columns = List.copyOf(uniqueColumns);

        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
    }

    /**
     * 从通用配置解析；缺省游标列时退化为按主键增量。
     */
    public static MySqlConnectorConfig from(ConnectorConfig config) {
        String url = config.require("url");
        String username = config.require("username");
        String password = config.get("password");
        String table = config.require("table");
        String idColumn = config.require("idColumn");
        String cursorColumn = config.get("cursorColumn");
        if (cursorColumn == null || cursorColumn.isBlank()) {
            cursorColumn = idColumn;
        }
        List<String> columns = parseColumns(config.require("columns"));
        int pageSize = parsePageSize(config.get("pageSize"));
        return new MySqlConnectorConfig(url, username, password, table, idColumn, cursorColumn, columns, pageSize);
    }

    private static List<String> parseColumns(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .toList();
    }

    private static int parsePageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE_SIZE;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("pageSize 必须是整数", e);
        }
    }

    private static void validateIdentifier(String identifier, String field) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("非法标识符（" + field + "）：" + identifier);
        }
    }

    private static void validateQualifiedIdentifier(String identifier, String field) {
        if (identifier == null) {
            throw new IllegalArgumentException("缺少数据源配置项：" + field);
        }
        String[] parts = identifier.split("\\.");
        if (parts.length == 0 || parts.length > MAX_TABLE_PARTS) {
            throw new IllegalArgumentException("非法标识符（" + field + "）：" + identifier);
        }
        for (String part : parts) {
            validateIdentifier(part, field);
        }
    }
}
