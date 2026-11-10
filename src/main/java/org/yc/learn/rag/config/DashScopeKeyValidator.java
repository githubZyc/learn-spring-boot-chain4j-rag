package org.yc.learn.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动时检查 DashScope 配置，避免模型 Bean 静默缺失或运行到第一次提问才报鉴权错误。
 *
 * @author zhengyanchuang
 */
@Component
public class DashScopeKeyValidator {

    public DashScopeKeyValidator(
            @Value("${langchain4j.community.dashscope.streaming-chat-model.api-key:}") String chatApiKey,
            @Value("${langchain4j.community.dashscope.embedding-model.api-key:}") String embeddingApiKey) {
        if (chatApiKey == null || chatApiKey.isBlank() || embeddingApiKey == null || embeddingApiKey.isBlank()) {
            throw new IllegalStateException("""
                    缺少 DashScope API Key。
                    请复制 .env.example 为 .env 并填写 DASHSCOPE_API_KEY，
                    或设置同名环境变量后重新启动。
                    """);
        }
    }
}
