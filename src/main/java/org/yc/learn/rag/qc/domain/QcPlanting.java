package org.yc.learn.rag.qc.domain;

import java.time.LocalDateTime;

/**
 * 手术质控：种植数据。
 *
 * @author zhengyanchuang
 */
public record QcPlanting(
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer regularPlantingSingle,
        Integer regularPlantingMulti,
        String regularPlantingPosition,
        Integer longPlantingSingle,
        Integer longPlantingMulti,
        String longPlantingPosition,
        String videoFileIds,
        String nurseName) {

    /**
     * 常规 + 长发种植总量。
     */
    public long totalPlanted() {
        return intValue(regularPlantingSingle) + intValue(regularPlantingMulti)
                + intValue(longPlantingSingle) + intValue(longPlantingMulti);
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }
}
