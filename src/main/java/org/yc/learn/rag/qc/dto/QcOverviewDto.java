package org.yc.learn.rag.qc.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 质控看板数据。
 *
 * @author zhengyanchuang
 */
public record QcOverviewDto(
        LocalDate from,
        LocalDate to,
        String departId,
        int totalCases,
        int shouldHaveData,
        int archived,
        int abnormalTerminated,
        int staleInProgress,
        long openFindings,
        long criticalFindings,
        double archiveRate,
        double avgArchiveDays,
        Map<String, Long> findingsByRule,
        Map<String, Long> findingsBySeverity,
        List<DepartRankDto> departRanking,
        List<QcFindingDto> topFindings) {

    public QcOverviewDto {
        findingsByRule = findingsByRule == null ? Map.of() : Map.copyOf(findingsByRule);
        findingsBySeverity = findingsBySeverity == null ? Map.of() : Map.copyOf(findingsBySeverity);
        departRanking = departRanking == null ? List.of() : List.copyOf(departRanking);
        topFindings = topFindings == null ? List.of() : List.copyOf(topFindings);
    }

    /**
     * 分院维度排名。
     */
    public record DepartRankDto(
            String departId,
            String departName,
            int cases,
            int findings,
            int archived,
            double archiveRate,
            double avgArchiveDays) {
    }
}
