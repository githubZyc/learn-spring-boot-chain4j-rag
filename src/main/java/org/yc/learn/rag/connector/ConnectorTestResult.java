package org.yc.learn.rag.connector;

/**
 * 数据源连接自检结果。
 *
 * @author zhengyanchuang
 */
public record ConnectorTestResult(boolean success, String message) {

    public static ConnectorTestResult ok(String message) {
        return new ConnectorTestResult(true, message);
    }

    public static ConnectorTestResult fail(String message) {
        return new ConnectorTestResult(false, message);
    }
}
