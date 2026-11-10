package org.yc.learn.rag.service;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.TokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yc.learn.rag.ai.MetadataKeys;
import org.yc.learn.rag.ai.RagAssistant;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.config.ManagedVirtualThreadExecutor;
import org.yc.learn.rag.dto.ChatRequest;
import org.yc.learn.rag.dto.SourceDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 将 langchain4j 的 TokenStream 适配为 SSE。
 *
 * @author zhengyanchuang
 */
@Service
public class ChatStreamService {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    private static final int SNIPPET_LENGTH = 200;

    private final RagAssistant assistant;
    private final ManagedVirtualThreadExecutor chatExecutor;
    private final AppProperties properties;

    public ChatStreamService(RagAssistant assistant,
                             @Qualifier("chatExecutor") ManagedVirtualThreadExecutor chatExecutor,
                             AppProperties properties) {
        this.assistant = assistant;
        this.chatExecutor = chatExecutor;
        this.properties = properties;
    }

    public SseEmitter stream(ChatRequest request) {
        SseEmitter emitter = new SseEmitter(properties.sse().timeoutMs());
        SafeEmitter out = new SafeEmitter(emitter);
        emitter.onCompletion(out::markClosed);
        emitter.onTimeout(out::markClosed);
        emitter.onError(error -> out.markClosed());

        chatExecutor.execute(() -> {
            try {
                out.send("meta", Map.of("conversationId", request.conversationId()));
                TokenStream tokenStream = assistant.chat(request.conversationId(), request.message());
                tokenStream
                        .onRetrieved(contents -> out.send("sources", toSources(contents)))
                        .onPartialResponse(token -> out.send("token", Map.of("text", token)))
                        .onCompleteResponse(response -> {
                            out.send("done", Map.of("conversationId", request.conversationId()));
                            out.complete();
                        })
                        .onError(error -> {
                            log.error("流式对话失败：conversationId={}", request.conversationId(), error);
                            out.send("error", Map.of("message", safeMessage(error)));
                            out.complete();
                        })
                        .start();
            } catch (Exception e) {
                log.error("启动流式对话失败：conversationId={}", request.conversationId(), e);
                out.send("error", Map.of("message", safeMessage(e)));
                out.complete();
            }
        });
        return emitter;
    }

    public void evictConversation(String conversationId) {
        assistant.evictChatMemory(conversationId);
    }

    private static List<SourceDto> toSources(List<Content> contents) {
        List<SourceDto> sources = new ArrayList<>();
        int index = 1;
        for (Content content : contents) {
            var segment = content.textSegment();
            var metadata = segment.metadata();
            Object score = content.metadata().get(ContentMetadata.SCORE);
            sources.add(new SourceDto(
                    index++,
                    metadata.getString(MetadataKeys.DOC_ID),
                    metadata.getString(MetadataKeys.FILE_NAME),
                    parseIndex(metadata.getString(MetadataKeys.INDEX)),
                    score instanceof Number number ? number.doubleValue() : 0.0,
                    snippet(segment.text())));
        }
        return sources;
    }

    private static Integer parseIndex(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= SNIPPET_LENGTH
                ? normalized
                : normalized.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "生成回答失败，请稍后重试" : message;
    }

    /**
     * SseEmitter 的回调可能来自不同线程，这里统一加锁，并保证只 complete 一次。
     */
    private static final class SafeEmitter {

        private final SseEmitter emitter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private SafeEmitter(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void send(String event, Object data) {
            if (closed.get()) {
                return;
            }
            synchronized (this) {
                if (closed.get()) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event()
                            .name(event)
                            .data(data, MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    // 客户端断开或连接已关闭，停止后续写入
                    closed.set(true);
                    log.debug("SSE 发送失败，客户端可能已断开：event={}", event, e);
                }
            }
        }

        void complete() {
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }

        void markClosed() {
            closed.set(true);
        }
    }
}
