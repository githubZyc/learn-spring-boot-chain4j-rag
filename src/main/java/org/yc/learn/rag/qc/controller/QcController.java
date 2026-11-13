package org.yc.learn.rag.qc.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.yc.learn.rag.dto.SqlQueryResponse;
import org.yc.learn.rag.qc.domain.QcFindingStatus;
import org.yc.learn.rag.qc.dto.QcAskRequest;
import org.yc.learn.rag.qc.dto.QcCaseDetailDto;
import org.yc.learn.rag.qc.dto.QcFindingDto;
import org.yc.learn.rag.qc.dto.QcOverviewDto;
import org.yc.learn.rag.qc.dto.QcPageDto;
import org.yc.learn.rag.qc.dto.QcRuleDto;
import org.yc.learn.rag.qc.dto.QcScanResultDto;
import org.yc.learn.rag.qc.rule.QcRuleRegistry;
import org.yc.learn.rag.qc.rule.QcSeverity;
import org.yc.learn.rag.qc.service.QcAskService;
import org.yc.learn.rag.qc.service.QcCaseService;
import org.yc.learn.rag.qc.service.QcFindingService;
import org.yc.learn.rag.qc.service.QcOverviewService;
import org.yc.learn.rag.qc.service.QcScanService;

import java.time.LocalDate;
import java.util.List;

/**
 * 手术质控助手接口。
 *
 * @author zhengyanchuang
 */
@RestController
@RequestMapping("/api/qc")
public class QcController {

    private final QcOverviewService overviewService;
    private final QcFindingService findingService;
    private final QcCaseService caseService;
    private final QcAskService askService;
    private final QcScanService scanService;
    private final QcRuleRegistry ruleRegistry;

    public QcController(QcOverviewService overviewService,
                        QcFindingService findingService,
                        QcCaseService caseService,
                        QcAskService askService,
                        QcScanService scanService,
                        QcRuleRegistry ruleRegistry) {
        this.overviewService = overviewService;
        this.findingService = findingService;
        this.caseService = caseService;
        this.askService = askService;
        this.scanService = scanService;
        this.ruleRegistry = ruleRegistry;
    }

    @GetMapping("/overview")
    public QcOverviewDto overview(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String departId) {
        return overviewService.overview(from, to, departId);
    }

    @GetMapping("/findings")
    public QcPageDto<QcFindingDto> findings(
            @RequestParam(required = false) QcFindingStatus status,
            @RequestParam(required = false) QcSeverity severity,
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String departId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return findingService.list(status, severity, ruleCode, departId, from, to, page, size);
    }

    @GetMapping("/cases/{recordId}")
    public QcCaseDetailDto caseDetail(@PathVariable long recordId) {
        return caseService.detail(recordId);
    }

    @PostMapping("/ask")
    public SqlQueryResponse ask(@Valid @RequestBody QcAskRequest request) {
        return askService.ask(request.question());
    }

    @PostMapping("/scan")
    public QcScanResultDto scan(@RequestParam(defaultValue = "30") int days) {
        return scanService.scan(days);
    }

    @PostMapping("/findings/{id}/ack")
    public QcFindingDto ack(@PathVariable long id,
                            @RequestParam(defaultValue = "anonymous") String operator) {
        return findingService.ack(id, operator);
    }

    @PostMapping("/findings/{id}/resolve")
    public QcFindingDto resolve(@PathVariable long id,
                                @RequestParam(defaultValue = "anonymous") String operator,
                                @RequestParam(required = false) String note) {
        return findingService.resolve(id, operator, note);
    }

    @PostMapping("/findings/{id}/ignore")
    public QcFindingDto ignore(@PathVariable long id,
                               @RequestParam(defaultValue = "anonymous") String operator,
                               @RequestParam(required = false) String note) {
        return findingService.ignore(id, operator, note);
    }

    @GetMapping("/rules")
    public List<QcRuleDto> rules() {
        return ruleRegistry.rules().stream()
                .map(rule -> new QcRuleDto(rule.code(), rule.name(), rule.severity(), true))
                .toList();
    }
}
