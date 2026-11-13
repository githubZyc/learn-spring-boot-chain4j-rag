package org.yc.learn.rag.qc.service;

import org.springframework.stereotype.Service;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.data.QcCaseLoader;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.dto.QcFindingDto;
import org.yc.learn.rag.qc.dto.QcOverviewDto;
import org.yc.learn.rag.qc.rule.QcFindingDraft;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 质控看板统计。
 * <p>
 * KPI 从当前病例实时计算，未闭环异常从应用库读取；两者分开，避免扫描延迟影响看板。
 *
 * @author zhengyanchuang
 */
@Service
public class QcOverviewService {

    private final QcCaseLoader caseLoader;
    private final QcScanService scanService;
    private final QcFindingService findingService;
    private final AppProperties properties;

    public QcOverviewService(QcCaseLoader caseLoader,
                             QcScanService scanService,
                             QcFindingService findingService,
                             AppProperties properties) {
        this.caseLoader = caseLoader;
        this.scanService = scanService;
        this.findingService = findingService;
        this.properties = properties;
    }

    public QcOverviewDto overview(LocalDate from, LocalDate to, String departId) {
        LocalDate end = to == null ? LocalDate.now() : to;
        int defaultDays = properties.qc() == null ? 30 : Math.max(1, properties.qc().scanDays());
        LocalDate start = from == null ? end.minusDays(defaultDays) : from;

        List<QcCase> cases = caseLoader.load(start, end, departId);
        List<QcFindingDraft> drafts = scanService.evaluate(cases);

        int totalCases = cases.size();
        int shouldHaveData = (int) cases.stream().filter(QcCase::shouldHaveData).count();
        List<QcCase> archived = cases.stream().filter(QcCase::archived).toList();
        int abnormalTerminated = (int) cases.stream().filter(QcCase::abnormalTerminated).count();
        int staleInProgress = (int) cases.stream()
                .filter(c -> c.qcStatus() == 1 && "FINISHED".equalsIgnoreCase(c.currentStage()))
                .count();

        ArchiveStats archiveStats = archiveStats(archived);
        Map<String, Long> findingsByRule = drafts.stream()
                .collect(Collectors.groupingBy(QcFindingDraft::ruleName, Collectors.counting()));
        Map<String, Long> findingsBySeverity = drafts.stream()
                .collect(Collectors.groupingBy(draft -> draft.severity().name(), Collectors.counting()));
        List<QcOverviewDto.DepartRankDto> ranking = departRanking(cases, drafts);

        return new QcOverviewDto(
                start,
                end,
                departId,
                totalCases,
                shouldHaveData,
                archived.size(),
                abnormalTerminated,
                staleInProgress,
                findingService.openCount(),
                findingService.criticalCount(),
                archiveStats.rate(),
                archiveStats.avgDays(),
                findingsByRule,
                findingsBySeverity,
                ranking,
                findingService.topOpen());
    }

    private List<QcOverviewDto.DepartRankDto> departRanking(List<QcCase> cases, List<QcFindingDraft> drafts) {
        Map<String, List<QcCase>> byDepart = cases.stream()
                .collect(Collectors.groupingBy(
                        qcCase -> qcCase.departId() == null ? "" : qcCase.departId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<QcOverviewDto.DepartRankDto> ranking = new ArrayList<>();
        for (Map.Entry<String, List<QcCase>> entry : byDepart.entrySet()) {
            List<QcCase> group = entry.getValue();
            List<QcCase> archived = group.stream().filter(QcCase::archived).toList();
            ArchiveStats stats = archiveStats(archived);
            int findings = (int) drafts.stream()
                    .filter(draft -> Objects.equals(draft.departId(), entry.getKey()))
                    .count();
            ranking.add(new QcOverviewDto.DepartRankDto(
                    entry.getKey(),
                    group.get(0).departName(),
                    group.size(),
                    findings,
                    archived.size(),
                    stats.rate(),
                    stats.avgDays()));
        }
        ranking.sort(Comparator.comparingInt(QcOverviewDto.DepartRankDto::findings).reversed()
                .thenComparing(QcOverviewDto.DepartRankDto::departName,
                        Comparator.nullsLast(String::compareTo)));
        return ranking;
    }

    private ArchiveStats archiveStats(List<QcCase> archived) {
        if (archived.isEmpty()) {
            return new ArchiveStats(0, 0);
        }
        int threshold = properties.qc() == null ? 7 : Math.max(1, properties.qc().archiveDays());
        int counted = 0;
        int onTime = 0;
        long totalDays = 0;
        for (QcCase qcCase : archived) {
            if (qcCase.filedTime() == null || qcCase.surgeryDate() == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(qcCase.surgeryDate(), qcCase.filedTime().toLocalDate());
            totalDays += days;
            counted++;
            if (days <= threshold) {
                onTime++;
            }
        }
        if (counted == 0) {
            return new ArchiveStats(0, 0);
        }
        double rate = onTime / (double) counted;
        double avgDays = totalDays / (double) counted;
        return new ArchiveStats(rate, Math.round(avgDays * 10) / 10.0);
    }

    private record ArchiveStats(double rate, double avgDays) {
    }
}
