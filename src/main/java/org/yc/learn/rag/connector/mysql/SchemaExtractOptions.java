package org.yc.learn.rag.connector.mysql;

import org.yc.learn.rag.connector.ConnectorConfig;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Schema 提取的选项，来源于数据源配置。
 * <p>
 * 数据库名优先取显式配置，其次从 JDBC URL 解析；表白名单为空表示同步全部业务表。
 * 表别名的用途是给没有中文注释的表补一个业务名称（例如 {@code e__dzbl=电子病历表}），
 * 否则「电子病历在哪个表」这类问题没有可召回的中文信号。
 *
 * @author zhengyanchuang
 */
public record SchemaExtractOptions(
        String database,
        Set<String> tableWhitelist,
        Set<String> extraSensitiveColumns,
        String defaultTimeColumn,
        Map<String, String> tableAliases) {

    public SchemaExtractOptions {
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("缺少数据库名：请配置 database 或在 URL 中指定");
        }
        tableWhitelist = tableWhitelist == null ? Set.of() : Set.copyOf(tableWhitelist);
        extraSensitiveColumns = extraSensitiveColumns == null ? Set.of() : Set.copyOf(extraSensitiveColumns);
        defaultTimeColumn = defaultTimeColumn == null || defaultTimeColumn.isBlank() ? null : defaultTimeColumn.trim();
        tableAliases = tableAliases == null ? Map.of() : Map.copyOf(tableAliases);
    }

    public static SchemaExtractOptions from(ConnectorConfig config) {
        String database = config.get("database");
        if (database == null || database.isBlank()) {
            database = parseDatabase(config.require("url"));
        }
        return new SchemaExtractOptions(
                database.trim(),
                parseCsv(config.get("tableWhitelist")),
                parseCsv(config.get("extraSensitiveColumns")),
                config.get("defaultTimeColumn"),
                parseAliases(config.get("tableAliases")));
    }

    /**
     * 从 {@code jdbc:mysql://host:3306/db?params} 中解析库名。
     */
    public static String parseDatabase(String url) {
        String prefix = "jdbc:mysql://";
        if (url == null || !url.startsWith(prefix)) {
            throw new IllegalArgumentException("MySQL 连接地址必须以 jdbc:mysql:// 开头");
        }
        String rest = url.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("MySQL 连接地址缺少数据库名");
        }
        String database = rest.substring(slash + 1);
        int end = database.length();
        int question = database.indexOf('?');
        if (question >= 0) {
            end = Math.min(end, question);
        }
        int semicolon = database.indexOf(';');
        if (semicolon >= 0) {
            end = Math.min(end, semicolon);
        }
        database = database.substring(0, end).trim();
        if (database.isEmpty()) {
            throw new IllegalArgumentException("MySQL 连接地址缺少数据库名");
        }
        return database;
    }

    private static Set<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(values::add);
        return Set.copyOf(values);
    }

    private static Map<String, String> parseAliases(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new IllegalArgumentException("tableAliases 格式应为 table=别名：" + trimmed);
            }
            aliases.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return Map.copyOf(aliases);
    }
}
