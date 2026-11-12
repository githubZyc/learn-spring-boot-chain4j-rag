package org.yc.learn.rag.connector;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 数据源连接器的注册表，同时作为「系统支持哪些数据源类型」的唯一来源。
 * <p>
 * Spring 会把所有 {@link KnowledgeConnector} 实现注入构造函数；
 * 新增数据源自动注册，同步流程只依赖本注册表，不依赖具体连接器。
 *
 * @author zhengyanchuang
 */
@Component
public class KnowledgeConnectorRegistry {

    private final Map<String, KnowledgeConnector> connectors;

    public KnowledgeConnectorRegistry(List<KnowledgeConnector> connectors) {
        Map<String, KnowledgeConnector> byType = new HashMap<>();
        for (KnowledgeConnector connector : connectors) {
            String key = normalizeType(connector.type());
            KnowledgeConnector previous = byType.put(key, connector);
            if (previous != null) {
                throw new IllegalStateException("数据源类型重复注册：" + key);
            }
        }
        this.connectors = Map.copyOf(byType);
    }

    /**
     * 按类型获取连接器；类型不支持时抛出 {@link IllegalArgumentException}。
     */
    public KnowledgeConnector get(String type) {
        KnowledgeConnector connector = connectors.get(normalizeType(type));
        if (connector == null) {
            throw new IllegalArgumentException("不支持的数据源类型：" + type);
        }
        return connector;
    }

    public boolean supports(String type) {
        return connectors.containsKey(normalizeType(type));
    }

    /**
     * 所有支持的数据源类型（小写），例如 {@code [mysql]}。
     */
    public Set<String> types() {
        return connectors.keySet();
    }

    private static String normalizeType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }
}
