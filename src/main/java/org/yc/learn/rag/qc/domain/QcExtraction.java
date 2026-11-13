package org.yc.learn.rag.qc.domain;

import java.time.LocalDateTime;

/**
 * 手术质控：取发 / 毛囊分离数据。
 *
 * @author zhengyanchuang
 */
public record QcExtraction(
        LocalDateTime extractionStartTime,
        LocalDateTime extractionEndTime,
        boolean extractionOnly,
        Long extractionCount,
        LocalDateTime regularSeparationStartTime,
        LocalDateTime regularSeparationEndTime,
        Integer regularSeparationSingle,
        Integer regularSeparationMulti,
        LocalDateTime longSeparationStartTime,
        LocalDateTime longSeparationEndTime,
        Integer longSeparationSingle,
        Integer longSeparationMulti,
        String videoFileIds,
        String nurseName) {

    /**
     * 常规 + 长发取发总量；仅拔毛时使用 {@code extraction_count}。
     */
    public long totalExtracted() {
        if (extractionOnly) {
            return extractionCount == null ? 0 : extractionCount;
        }
        return intValue(regularSeparationSingle) + intValue(regularSeparationMulti)
                + intValue(longSeparationSingle) + intValue(longSeparationMulti);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
