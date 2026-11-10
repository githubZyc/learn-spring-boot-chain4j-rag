package org.yc.learn.rag.config;

import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yc.learn.rag.ai.MetadataKeys;

import java.util.List;

/**
 * 自定义检索增强器。
 * <p>
 * 默认注入器只拼接 chunk 正文，不包含元数据；这里把文件名一起注入，
 * 模型才能在回答中按文件名标注引用。定义 RetrievalAugmentor Bean 后，
 * @AiService 会优先使用它（ContentRetriever 仍然由 RagAutoConfig 提供）。
 *
 * @author zhengyanchuang
 */
@Configuration
public class RagConfig {

    private static final PromptTemplate PROMPT_TEMPLATE = PromptTemplate.from("""
            {{userMessage}}

            请仅依据以下资料回答用户问题；如果资料中没有答案，请明确说明“知识库中没有找到相关内容”。
            资料格式为：chunk 正文 + 来源文件名。

            {{contents}}
            """);

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever) {
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .contentInjector(DefaultContentInjector.builder()
                        .promptTemplate(PROMPT_TEMPLATE)
                        .metadataKeysToInclude(List.of(MetadataKeys.FILE_NAME))
                        .build())
                .build();
    }
}
