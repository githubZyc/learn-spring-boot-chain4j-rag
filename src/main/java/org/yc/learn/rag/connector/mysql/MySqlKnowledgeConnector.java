package org.yc.learn.rag.connector.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yc.learn.rag.connector.ConnectorBatch;
import org.yc.learn.rag.connector.ConnectorConfig;
import org.yc.learn.rag.connector.ConnectorTestResult;
import org.yc.learn.rag.connector.KnowledgeConnector;
import org.yc.learn.rag.connector.RawDocument;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MySQL 数据源连接器：按游标分页读取表 / 视图，并把每行渲染成一段文本。
 * <p>
 * 只做「查 → 转文本」，不做切分和向量化；同步调度、状态流转由上层服务负责。
 * 所有标识符都经过 {@link MySqlConnectorConfig} 白名单校验，值统一走
 * {@link PreparedStatement} 参数绑定，杜绝拼接用户输入。
 *
 * @author zhengyanchuang
 */
@Component
public class MySqlKnowledgeConnector implements KnowledgeConnector {

    private static final Logger log = LoggerFactory.getLogger(MySqlKnowledgeConnector.class);

    private static final String TYPE = "mysql";
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ConnectorTestResult test(ConnectorConfig config) {
        long start = System.nanoTime();
        try (Connection connection = MySqlJdbc.open(
                config.require("url"), config.require("username"), config.get("password"))) {
            if (connection.isValid(3)) {
                long costMs = (System.nanoTime() - start) / 1_000_000;
                return ConnectorTestResult.ok("连接成功，耗时 " + costMs + "ms");
            }
            return ConnectorTestResult.fail("连接无效：数据库未在 3 秒内响应");
        } catch (Exception e) {
            // 只记录异常摘要，避免把密码等连接串细节写进日志
            log.warn("MySQL 连接测试失败：{}", e.getMessage());
            return ConnectorTestResult.fail("连接失败：" + MySqlJdbc.rootMessage(e));
        }
    }

    @Override
    public ConnectorBatch fetch(ConnectorConfig config, String cursor, int limit) {
        MySqlConnectorConfig mysql = MySqlConnectorConfig.from(config);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        // 调用方的 limit 不能突破数据源配置的单页上限，防止内存被大页撑爆
        int pageSize = Math.min(limit, mysql.pageSize());

        String sql = buildSql(mysql, cursor != null);
        try (Connection connection = MySqlJdbc.open(mysql.url(), mysql.username(), mysql.password());
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int index = 1;
            if (cursor != null) {
                statement.setString(index++, cursor);
            }
            statement.setInt(index, pageSize);
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);

            List<RawDocument> documents = new ArrayList<>();
            String nextCursor = null;
            int rowCount = 0;
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    Map<String, String> row = readRow(rs, mysql.columns());
                    Object cursorValue = rs.getObject(mysql.cursorColumn());
                    if (cursorValue != null) {
                        nextCursor = cursorValue.toString();
                    }
                    // 主键为空无法生成稳定文档 ID，跳过，避免重复同步产生脏数据
                    String externalId = row.get(mysql.idColumn());
                    String content = renderContent(mysql.columns(), row);
                    if (externalId == null || externalId.isBlank() || content.isBlank()) {
                        continue;
                    }
                    documents.add(new RawDocument(
                            mysql.table() + "#" + externalId,
                            mysql.table() + "#" + externalId,
                            content,
                            row,
                            toInstant(cursorValue)));
                }
            }
            // 用读取行数而非文档数判断：跳过空行时不能提前结束同步
            return new ConnectorBatch(documents, nextCursor, rowCount >= pageSize);
        } catch (SQLException e) {
            log.error("MySQL 分页拉取失败：table={}, cursor={}", mysql.table(), cursor, e);
            throw new IllegalStateException("读取数据源失败：" + MySqlJdbc.rootMessage(e), e);
        }
    }

    private static Map<String, String> readRow(ResultSet rs, List<String> columns) throws SQLException {
        Map<String, String> row = new LinkedHashMap<>();
        for (String column : columns) {
            Object value = rs.getObject(column);
            if (value != null) {
                row.put(column, value.toString());
            }
        }
        return row;
    }

    private static String renderContent(List<String> columns, Map<String, String> row) {
        return columns.stream()
                .map(column -> {
                    String value = row.get(column);
                    return value == null || value.isBlank() ? null : column + ": " + value;
                })
                .filter(line -> line != null)
                .collect(Collectors.joining("\n"));
    }

    private static String buildSql(MySqlConnectorConfig config, boolean hasCursor) {
        String columns = config.columns().stream()
                .map(MySqlKnowledgeConnector::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String cursorColumn = quoteIdentifier(config.cursorColumn());

        StringBuilder sql = new StringBuilder("SELECT ")
                .append(columns)
                .append(" FROM ")
                .append(quoteQualifiedIdentifier(config.table()));
        if (hasCursor) {
            sql.append(" WHERE ").append(cursorColumn).append(" > ?");
        }
        return sql.append(" ORDER BY ").append(cursorColumn).append(" ASC LIMIT ?").toString();
    }

    private static String quoteQualifiedIdentifier(String identifier) {
        return java.util.Arrays.stream(identifier.split("\\."))
                .map(MySqlKnowledgeConnector::quoteIdentifier)
                .collect(Collectors.joining("."));
    }

    private static String quoteIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    private static Instant toInstant(Object cursorValue) {
        if (cursorValue instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (cursorValue instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        }
        return null;
    }
}
