package org.yc.learn.rag.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为 @AiService 提供按会话隔离的聊天记忆。
 *
 * @author zhengyanchuang
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatMemoryProvider chatMemoryProvider(AppProperties properties) {
        int maxMessages = properties.memory().maxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .build();
    }
}
