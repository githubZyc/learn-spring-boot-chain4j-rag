package org.yc.learn.rag.dto;

import java.time.Instant;

/**
 * 统一错误返回体。
 *
 * @author zhengyanchuang
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path) {
}
