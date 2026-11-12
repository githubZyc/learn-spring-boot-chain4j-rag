package org.yc.learn.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 后台执行器。Java 21 虚拟线程足够轻量，阻塞等待模型 / 向量库不会占满平台线程。
 * <p>
 * 关闭时留出有限宽限时间，避免大批量入库让进程无法退出。
 *
 * @author zhengyanchuang
 */
@Configuration
public class AsyncConfig {

    /** 流式对话使用 */
    @Bean(destroyMethod = "shutdown")
    public ManagedVirtualThreadExecutor chatExecutor() {
        return new ManagedVirtualThreadExecutor("chat", Duration.ofSeconds(5));
    }

    /** 文档异步入库使用，和对话隔离，避免大批量入库影响聊天 */
    @Bean(destroyMethod = "shutdown")
    public ManagedVirtualThreadExecutor ingestionExecutor() {
        return new ManagedVirtualThreadExecutor("ingestion", Duration.ofSeconds(10));
    }

    /** Schema 同步独立执行器，避免大库元数据同步阻塞文档入库 */
    @Bean(destroyMethod = "shutdown")
    public ManagedVirtualThreadExecutor schemaExecutor() {
        return new ManagedVirtualThreadExecutor("schema", Duration.ofSeconds(10));
    }
}
