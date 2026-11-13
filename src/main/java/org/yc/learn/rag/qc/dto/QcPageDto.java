package org.yc.learn.rag.qc.dto;

import java.util.List;

/**
 * 简单分页返回体，避免直接暴露 Spring Data 的 Page 结构。
 *
 * @author zhengyanchuang
 */
public record QcPageDto<T>(List<T> items, long total, int page, int size) {

    public QcPageDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
