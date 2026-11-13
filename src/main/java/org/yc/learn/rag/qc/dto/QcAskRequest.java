package org.yc.learn.rag.qc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 质控自然语言问答请求。
 *
 * @author zhengyanchuang
 */
public record QcAskRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过 500 个字符")
        String question) {
}
