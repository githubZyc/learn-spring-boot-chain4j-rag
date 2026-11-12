package org.yc.learn.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 「找表」请求。
 *
 * @author zhengyanchuang
 */
public record SchemaAskRequest(
        @NotBlank(message = "问题不能为空")
        @Size(max = 500, message = "问题不能超过 500 个字符")
        String question,

        Integer maxResults) {
}
