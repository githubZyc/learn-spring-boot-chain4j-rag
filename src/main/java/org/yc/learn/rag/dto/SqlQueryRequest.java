package org.yc.learn.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自然语言查数请求。
 *
 * @author zhengyanchuang
 */
public record SqlQueryRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过 500 个字符")
        String question) {
}
