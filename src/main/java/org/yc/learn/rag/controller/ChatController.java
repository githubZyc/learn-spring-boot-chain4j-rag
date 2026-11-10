package org.yc.learn.rag.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.yc.learn.rag.dto.ChatRequest;
import org.yc.learn.rag.service.ChatStreamService;

/**
 * 知识库问答接口。
 *
 * @author zhengyanchuang
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatStreamService chatStreamService;

    public ChatController(ChatStreamService chatStreamService) {
        this.chatStreamService = chatStreamService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
        return chatStreamService.stream(request);
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearConversation(@PathVariable String conversationId) {
        chatStreamService.evictConversation(conversationId);
    }
}
