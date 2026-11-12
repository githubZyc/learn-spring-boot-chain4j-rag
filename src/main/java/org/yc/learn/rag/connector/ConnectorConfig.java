package org.yc.learn.rag.connector;

import java.util.Map;

/**
 * 数据源连接配置的通用载体。
 * <p>
 * 不同连接器的配置项差异很大，这里用字符串键值对承载；各连接器负责在
 * {@code from(...)} 中解析为自己的强类型配置并做校验，避免核心服务感知具体数据源。
 *
 * @author zhengyanchuang
 */
public record ConnectorConfig(Map<String, String> properties) {

    public ConnectorConfig {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    /**
     * 读取配置项，不存在时返回 {@code null}。
     */
    public String get(String key) {
        return properties.get(key);
    }

    /**
     * 读取必填配置项，缺失或空白时抛出 {@link IllegalArgumentException}。
     */
    public String require(String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少数据源配置项：" + key);
        }
        return value;
    }
}
