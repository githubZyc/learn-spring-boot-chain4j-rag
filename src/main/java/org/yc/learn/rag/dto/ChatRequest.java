package org.yc.learn.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 流式问答请求。
 *
 * @author zhengyanchuang
 */
public record ChatRequest(
        @NotBlank(message = "conversationId 不能为空")
        @Pattern(regexp = "[A-Za-z0-9_-]{1,64}", message = "conversationId 只能包含字母、数字、下划线和中划线，长度 1-64")
        String conversationId,

        @NotBlank(message = "message 不能为空")
        @Size(max = 4000, message = "message 不能超过 4000 个字符")
        String message) {
}
