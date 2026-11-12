package org.yc.learn.rag.connector.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * MySQL JDBC 连接的统一入口，集中管理超时与异常摘要。
 * <p>
 * 阻塞式数据库调用必须有超时上限，否则一个慢库会占满后台同步线程。
 *
 * @author zhengyanchuang
 */
final class MySqlJdbc {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    private MySqlJdbc() {
    }

    static Connection open(String url, String username, String password) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password == null ? "" : password);
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        properties.setProperty("socketTimeout", String.valueOf(SOCKET_TIMEOUT_MS));
        return DriverManager.getConnection(url, properties);
    }

    /**
     * 取最内层异常的消息，避免把整条异常链暴露给用户。
     */
    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
