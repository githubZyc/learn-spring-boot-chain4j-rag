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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 提取指定库下所有（或白名单内）的表结构。
     */
    public List<SchemaDocument> extract(ConnectorConfig config) {
        SchemaExtractOptions options = SchemaExtractOptions.from(config);
        try (Connection connection = MySqlJdbc.open(
                config.require("url"), config.require("username"), config.get("password"))) {

            Map<String, TableMeta> tables = loadTables(connection, options);
            Map<String, List<SchemaColumn>> columnsByTable = loadColumns(connection, options);
            Map<String, List<SchemaIndex>> indexesByTable = loadIndexes(connection, options);

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

    private static Map<String, TableMeta> loadTables(Connection connection, SchemaExtractOptions options)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, options.database());
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

    private static Map<String, List<SchemaColumn>> loadColumns(Connection connection, SchemaExtractOptions options)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                       COLUMN_KEY, COLUMN_DEFAULT, COLUMN_COMMENT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, ORDINAL_POSITION""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, options.database());
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

    private static Map<String, List<SchemaIndex>> loadIndexes(Connection connection, SchemaExtractOptions options)
            throws SQLException {
        String sql = """
                SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME, NON_UNIQUE
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, options.database());
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
