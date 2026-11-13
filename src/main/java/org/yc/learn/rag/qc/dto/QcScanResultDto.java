package org.yc.learn.rag.qc.dto;

import java.time.Instant;

/**
 * 一次质控扫描的结果。
 *
 * @author zhengyanchuang
 */
public record QcScanResultDto(
        int totalCases,
        int totalFindings,
        int created,
        int updated,
        int reopened,
        int closed,
        long costMs,
        Instant scannedAt) {
}
