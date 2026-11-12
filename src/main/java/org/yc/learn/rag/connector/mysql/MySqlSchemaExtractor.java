package org.yc.learn.rag.connector.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yc.learn.rag.connector.ConnectorConfig;
import org.yc.learn.rag.connector.DefaultTimeColumnResolver;
import org.yc.learn.rag.connector.SchemaColumn;
import org.yc.learn.rag.connector.SchemaDocument;
import org.yc.learn.rag.connector.SchemaIndex;
import org.yc.learn.rag.connector.SensitiveColumnDetector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 从 MySQL {@code information_schema} 提取表结构，产出「一表一文档」。
 * <p>
 * 只读取元数据，不读取业务数据；表注释与列注释会一并写入 Schema 文档，
 * 它们决定了「找表」的召回质量。整个提取过程使用只读查询，并设置语句超时。
 *
 * @author zhengyanchuang
 */
@Component
public class MySqlSchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(MySqlSchemaExtractor.class);
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * 提取指定库下所有（或白名单内）的表结构。
     */
    public List<SchemaDocument> extract(ConnectorConfig config) {
        return extract(config, null);
    }

    /**
     * 只提取一张表的结构，供 NL2SQL 在查询时获取最新字段信息。
     */
    public SchemaDocument extractTable(ConnectorConfig config, String tableName) {
        List<SchemaDocument> documents = extractTables(config, Set.of(tableName));
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("表不存在或未授权：" + tableName);
        }
        return documents.get(0);
    }

    /**
     * 批量提取多张表的结构；NL2SQL 需要做多表关联时使用。
     */
    public List<SchemaDocument> extractTables(ConnectorConfig config, Collection<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一张表");
        }
        List<String> validated = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            validateTableName(tableName);
            validated.add(tableName);
        }
        return extract(config, validated);
    }

    /**
     * 列出当前库中所有业务表，供 NL2SQL 校验模型引用的表是否存在。
     */
    public Set<String> listTableNames(ConnectorConfig config) {
        SchemaExtractOptions options = SchemaExtractOptions.from(config);
        String sql = """
                SELECT TABLE_NAME
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME""";
        try (Connection connection = MySqlJdbc.open(
                config.require("url"), config.require("username"), config.get("password"));
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, options.database());
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = statement.executeQuery()) {
                Set<String> names = new LinkedHashSet<>();
                while (rs.next()) {
                    names.add(rs.getString("TABLE_NAME"));
                }
                return names;
            }
        } catch (SQLException e) {
            log.error("读取表清单失败：database={}", options.database(), e);
            throw new IllegalStateException("读取表清单失败：" + MySqlJdbc.rootMessage(e), e);
        }
    }

    private static void validateTableName(String tableName) {
        if (tableName == null || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("非法表名：" + tableName);
        }
    }

    private List<SchemaDocument> extract(ConnectorConfig config, Collection<String> tableFilter) {
        SchemaExtractOptions options = SchemaExtractOptions.from(config);
        try (Connection connection = MySqlJdbc.open(
                config.require("url"), config.require("username"), config.get("password"))) {

            Map<String, TableMeta> tables = loadTables(connection, options, tableFilter);
            Map<String, List<SchemaColumn>> columnsByTable = loadColumns(connection, options, tableFilter);
            Map<String, List<SchemaIndex>> indexesByTable = loadIndexes(connection, options, tableFilter);

            List<SchemaDocument> documents = new ArrayList<>(tables.size());
            for (TableMeta table : tables.values()) {
                List<SchemaColumn> columns = columnsByTable.get(table.name());
                if (columns == null || columns.isEmpty()) {
                    // 没有列的视图/异常表跳过，避免写入空文档污染检索
                    continue;
                }
                documents.add(new SchemaDocument(
                        options.database(),
                        table.name(),
                        displayName(options, table),
                        primaryKeyOf(columns),
                        columns,
                        indexesByTable.getOrDefault(table.name(), List.of()),
                        resolveDefaultTimeColumn(options.defaultTimeColumn(), columns),
                        sensitiveColumnsOf(columns, options.extraSensitiveColumns())));
            }
            log.info("Schema 提取完成：database={}, tables={}", options.database(), documents.size());
            return documents;
        } catch (SQLException e) {
            log.error("Schema 提取失败：database={}", options.database(), e);
            throw new IllegalStateException("读取数据库结构失败：" + MySqlJdbc.rootMessage(e), e);
        }
    }

    private static Map<String, TableMeta> loadTables(Connection connection, SchemaExtractOptions options,
                                                     Collection<String> tableFilter) throws SQLException {
        Collection<String> filter = normalizeFilter(tableFilter);
        StringBuilder sql = new StringBuilder("""
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'""");
        if (filter != null) {
            sql.append(" AND TABLE_NAME IN (").append(placeholders(filter.size())).append(")");
        }
        sql.append(" ORDER BY TABLE_NAME");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, options.database());
            bindTableFilter(statement, 2, filter);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, TableMeta> tables = new LinkedHashMap<>();
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (!options.tableWhitelist().isEmpty() && !options.tableWhitelist().contains(name)) {
                        continue;
                    }
                    tables.put(name, new TableMeta(name, rs.getString("TABLE_COMMENT")));
                }
                return tables;
            }
        }
    }

    private static Map<String, List<SchemaColumn>> loadColumns(Connection connection, SchemaExtractOptions options,
                                                               Collection<String> tableFilter) throws SQLException {
        Collection<String> filter = normalizeFilter(tableFilter);
        StringBuilder sql = new StringBuilder("""
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                       COLUMN_KEY, COLUMN_DEFAULT, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?""");
        if (filter != null) {
            sql.append(" AND TABLE_NAME IN (").append(placeholders(filter.size())).append(")");
        }
        sql.append(" ORDER BY TABLE_NAME, ORDINAL_POSITION");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, options.database());
            bindTableFilter(statement, 2, filter);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, List<SchemaColumn>> byTable = new LinkedHashMap<>();
                while (rs.next()) {
                    String table = rs.getString("TABLE_NAME");
                    byTable.computeIfAbsent(table, key -> new ArrayList<>()).add(new SchemaColumn(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("COLUMN_TYPE"),
                            "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            "PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY")),
                            rs.getString("COLUMN_COMMENT"),
                            rs.getString("COLUMN_DEFAULT")));
                }
                return byTable;
            }
        }
    }

    private static Map<String, List<SchemaIndex>> loadIndexes(Connection connection, SchemaExtractOptions options,
                                                              Collection<String> tableFilter) throws SQLException {
        Collection<String> filter = normalizeFilter(tableFilter);
        StringBuilder sql = new StringBuilder("""
                SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?""");
        if (filter != null) {
            sql.append(" AND TABLE_NAME IN (").append(placeholders(filter.size())).append(")");
        }
        sql.append(" ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX");
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, options.database());
            bindTableFilter(statement, 2, filter);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet rs = statement.executeQuery()) {
                Map<String, LinkedHashMap<String, IndexBuilder>> grouped = new LinkedHashMap<>();
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    if (column == null) {
                        // MySQL 8 函数索引没有列名，跳过
                        continue;
                    }
                    String table = rs.getString("TABLE_NAME");
                    String index = rs.getString("INDEX_NAME");
                    boolean unique = rs.getInt("NON_UNIQUE") == 0;
                    grouped.computeIfAbsent(table, key -> new LinkedHashMap<>())
                            .computeIfAbsent(index, key -> new IndexBuilder(key, unique))
                            .columns.add(column);
                }
                Map<String, List<SchemaIndex>> byTable = new LinkedHashMap<>();
                grouped.forEach((table, indexes) -> byTable.put(table,
                        indexes.values().stream().map(IndexBuilder::toSchemaIndex).toList()));
                return byTable;
            }
        }
    }

    private static Collection<String> normalizeFilter(Collection<String> tableFilter) {
        return tableFilter == null || tableFilter.isEmpty() ? null : tableFilter;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static void bindTableFilter(PreparedStatement statement, int startIndex,
                                        Collection<String> tableFilter) throws SQLException {
        if (tableFilter == null) {
            return;
        }
        int index = startIndex;
        for (String table : tableFilter) {
            statement.setString(index++, table);
        }
    }

    /**
     * 别名优先于数据库注释：别名是人工补充的业务名称，通常比空注释更准确。
     */
    private static String displayName(SchemaExtractOptions options, TableMeta table) {
        String alias = options.tableAliases().get(table.name());
        return alias != null && !alias.isBlank() ? alias : table.comment();
    }

    private static String primaryKeyOf(List<SchemaColumn> columns) {
        return columns.stream()
                .filter(SchemaColumn::primaryKey)
                .map(SchemaColumn::name)
                .collect(Collectors.joining(","));
    }

    private static List<String> sensitiveColumnsOf(List<SchemaColumn> columns, java.util.Set<String> extra) {
        return columns.stream()
                .map(SchemaColumn::name)
                .filter(name -> SensitiveColumnDetector.isSensitive(name, extra))
                .toList();
    }

    private static String resolveDefaultTimeColumn(String override, List<SchemaColumn> columns) {
        if (override != null) {
            return columns.stream()
                    .filter(column -> column.name().equalsIgnoreCase(override))
                    .map(SchemaColumn::name)
                    .findFirst()
                    // 配置的列不存在时退化为自动推断，避免生成非法 SQL
                    .orElseGet(() -> DefaultTimeColumnResolver.resolve(columns));
        }
        return DefaultTimeColumnResolver.resolve(columns);
    }

    private record TableMeta(String name, String comment) {
    }

    private static final class IndexBuilder {

        private final String name;
        private final boolean unique;
        private final List<String> columns = new ArrayList<>();

        private IndexBuilder(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }

        private SchemaIndex toSchemaIndex() {
            return new SchemaIndex(name, unique, columns);
        }
    }
}
