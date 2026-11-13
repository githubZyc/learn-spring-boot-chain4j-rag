package org.yc.learn.rag.qc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.qc.data.QcCaseLoader;
import org.yc.learn.rag.qc.domain.QcCase;
import org.yc.learn.rag.qc.dto.QcScanResultDto;
import org.yc.learn.rag.qc.rule.QcFindingDraft;
import org.yc.learn.rag.qc.rule.QcRule;
import org.yc.learn.rag.qc.rule.QcRuleRegistry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 质控扫描：加载病例、执行规则、持久化异常。
 *
 * @author zhengyanchuang
 */
@Service
public class QcScanService {

    private static final Logger log = LoggerFactory.getLogger(QcScanService.class);

    private final QcCaseLoader caseLoader;
    private final QcRuleRegistry ruleRegistry;
    private final QcFindingService findingService;
    private final AppProperties properties;

    public QcScanService(QcCaseLoader caseLoader,
                         QcRuleRegistry ruleRegistry,
                         QcFindingService findingService,
                         AppProperties properties) {
        this.caseLoader = caseLoader;
        this.ruleRegistry = ruleRegistry;
        this.findingService = findingService;
        this.properties = properties;
    }

    /**
     * 对已加载病例执行全部规则，返回草稿；不写库，供看板实时计算。
     */
    public List<QcFindingDraft> evaluate(List<QcCase> cases) {
        List<QcFindingDraft> drafts = new ArrayList<>();
        List<QcRule> rules = ruleRegistry.rules();
        for (QcCase qcCase : cases) {
            for (QcRule rule : rules) {
                drafts.addAll(rule.evaluate(qcCase));
            }
        }
        return drafts;
    }

    public QcScanResultDto scan(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.max(1, days));
        return scan(from, to, null);
    }

    public QcScanResultDto scan(LocalDate from, LocalDate to, String departId) {
        long start = System.nanoTime();
        List<QcCase> cases = caseLoader.load(from, to, departId);
        List<QcFindingDraft> drafts = evaluate(cases);
        QcFindingService.ScanApplyResult applied = findingService.applyScan(cases, drafts);
        long costMs = (System.nanoTime() - start) / 1_000_000;
        log.info("质控扫描完成：cases={}, findings={}, created={}, updated={}, reopened={}, closed={}, costMs={}",
                cases.size(), drafts.size(), applied.created(), applied.updated(),
                applied.reopened(), applied.closed(), costMs);
        return new QcScanResultDto(
                cases.size(),
                drafts.size(),
                applied.created(),
                applied.updated(),
                applied.reopened(),
                applied.closed(),
                costMs,
                Instant.now());
    }

    public int defaultScanDays() {
        AppProperties.Qc qc = properties.qc();
        return qc == null || qc.scanDays() <= 0 ? 30 : qc.scanDays();
    }
}
