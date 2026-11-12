package org.yc.learn.rag.connector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 连接器注册表的纯逻辑测试，不启动 Spring 上下文。
 *
 * @author zhengyanchuang
 */
class KnowledgeConnectorRegistryTest {

    @Test
    void registersConnectorByTypeIgnoringCase() {
        KnowledgeConnectorRegistry registry = new KnowledgeConnectorRegistry(List.of(new StubConnector("mysql")));

        assertThat(registry.supports("MySQL")).isTrue();
        assertThat(registry.get("MYSQL")).isSameAs(registry.get("mysql"));
        assertThat(registry.types()).containsExactly("mysql");
    }

    @Test
    void rejectsDuplicateType() {
        assertThatThrownBy(() -> new KnowledgeConnectorRegistry(
                List.of(new StubConnector("mysql"), new StubConnector("MYSQL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复注册");
    }

    @Test
    void rejectsUnknownType() {
        KnowledgeConnectorRegistry registry = new KnowledgeConnectorRegistry(List.of(new StubConnector("mysql")));

        assertThat(registry.supports("postgres")).isFalse();
        assertThatThrownBy(() -> registry.get("postgres"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的数据源类型");
    }

    private record StubConnector(String type) implements KnowledgeConnector {

        @Override
        public ConnectorTestResult test(ConnectorConfig config) {
            return ConnectorTestResult.ok("ok");
        }

        @Override
        public ConnectorBatch fetch(ConnectorConfig config, String cursor, int limit) {
            return ConnectorBatch.empty();
        }
    }
}
