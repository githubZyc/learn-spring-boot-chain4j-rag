package org.yc.learn.rag.qc.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 一例手术质控的完整视图：主记录 + 四个阶段子表。
 * <p>
 * 这是外部 MySQL 的只读数据快照，不映射为 JPA 实体；内部异常、闭环状态存在应用库。
 *
 * @author zhengyanchuang
 */
public record QcCase(
        long id,
        String recordNo,
        String departId,
        String departName,
        LocalDate surgeryDate,
        String currentStage,
        int qcStatus,
        String transplantTechnique,
        String operationId,
        String qcNurseId,
        String qcNurseName,
        String accountId,
        String accountCode,
        String accountName,
        String caseId,
        String dzblNo,
        String chargeOrderId,
        LocalDateTime surgeryStartTime,
        LocalDateTime surgeryEndTime,
        LocalDateTime filedTime,
        String filedBy,
        QcSetup setup,
        QcAnesthesia anesthesia,
        QcExtraction extraction,
        QcPlanting planting) {

    private static final int STATUS_CANCELLED = 5;

    /**
     * 状态 1~4 的病例应当有完整的质控数据；0 未开始、5 失效不参与缺项考核。
     */
    public boolean shouldHaveData() {
        return qcStatus >= 1 && qcStatus <= 4;
    }

    public boolean archived() {
        return qcStatus == 4;
    }

    public boolean abnormalTerminated() {
        return qcStatus == 3;
    }

    public boolean invalid() {
        return qcStatus == STATUS_CANCELLED || qcStatus == -STATUS_CANCELLED;
    }

    /**
     * 当前阶段是否已达到目标阶段；未知阶段返回 false。
     */
    public boolean stageAtLeast(String stage) {
        int current = stageIndex(currentStage);
        int target = stageIndex(stage);
        return current > 0 && target > 0 && current >= target;
    }

    /**
     * 植发技术字段可能是逗号分隔的多选值。
     */
    public boolean hasTechnique(int technique) {
        if (transplantTechnique == null || transplantTechnique.isBlank()) {
            return false;
        }
        String expected = String.valueOf(technique);
        for (String part : transplantTechnique.split(",")) {
            if (part.trim().equals(expected)) {
                return true;
            }
        }
        return false;
    }

    public static int stageIndex(String stage) {
        if (stage == null) {
            return 0;
        }
        return switch (stage.toUpperCase(Locale.ROOT)) {
            case "SETUP" -> 1;
            case "ANESTHESIA" -> 2;
            case "EXTRACTION" -> 3;
            case "PLANTING" -> 4;
            case "FINISHED" -> 5;
            default -> 0;
        };
    }
}
