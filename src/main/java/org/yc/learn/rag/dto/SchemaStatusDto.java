package org.yc.learn.rag.dto;

import java.time.Instant;

/**
 * 数据库知识库的同步状态。
 * <p>
 * {@code configured=false} 表示没有配置数据源；{@code status} 取值为
 * {@code IDLE / RUNNING / READY / FAILED}。
 *
 * @author zhengyanchuang
 */
public record SchemaStatusDto(
        boolean configured,
        String database,
        String collection,
        String status,
        int tableCount,
        String error,
        Instant startedAt,
        Instant finishedAt) {
}
