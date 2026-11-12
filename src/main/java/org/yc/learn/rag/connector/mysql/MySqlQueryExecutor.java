package org.yc.learn.rag.connector.mysql;

import org.springframework.stereotype.Component;
import org.yc.learn.rag.connector.ConnectorConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行 NL2SQL 产出的只读查询。
 * <p>
 * 安全边界在 {@code SqlValidator}：只有通过校验的语句才会到这里。
 * 这里再叠加三层保护：只读连接、最大行数、语句超时。
 *
 * @author zhengyanchuang
 */
@Component
public class MySqlQueryExecutor {

    public QueryResult execute(ConnectorConfig config, String sql, int maxRows, int timeoutSeconds) {
        try (Connection connection = MySqlJdbc.open(
                config.require("url"), config.require("username"), config.get("password"))) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(timeoutSeconds);
                statement.setMaxRows(maxRows);
                try (ResultSet rs = statement.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    List<String> columns = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }

                    List<List<String>> rows = new ArrayList<>();
                    while (rs.next()) {
                        List<String> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(toText(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return new QueryResult(columns, rows);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SQL 执行失败：" + MySqlJdbc.rootMessage(e), e);
        }
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[]) {
            return "[binary]";
        }
        return value.toString();
    }
}
