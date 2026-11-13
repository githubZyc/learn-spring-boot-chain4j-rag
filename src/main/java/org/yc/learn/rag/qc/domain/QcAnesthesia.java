package org.yc.learn.rag.qc.domain;

import java.time.LocalDateTime;

/**
 * 手术质控：麻醉数据。
 *
 * @author zhengyanchuang
 */
public record QcAnesthesia(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        String videoFileIds,
        String operatorName,
        String remark) {
}
