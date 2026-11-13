package org.yc.learn.rag.qc.domain;

/**
 * 手术质控：开台配置数据。
 *
 * @author zhengyanchuang
 */
public record QcSetup(
        String operatingRoomName,
        String extractionNeedle,
        String extractionArea,
        String difficultyFactorName,
        String mentorApprentice,
        String mentorApprenticeSon,
        String transplantTechnique,
        String videoFileIds) {
}
