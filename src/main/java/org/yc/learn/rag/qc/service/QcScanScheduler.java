package org.yc.learn.rag.qc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日质控扫描。手动扫描接口不受此开关影响。
 *
 * @author zhengyanchuang
 */
@Component
@ConditionalOnProperty(prefix = "app.qc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QcScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(QcScanScheduler.class);

    private final QcScanService scanService;

    public QcScanScheduler(QcScanService scanService) {
        this.scanService = scanService;
    }

    @Scheduled(cron = "${app.qc.scan-cron:0 0 6 * * *}")
    public void scanDaily() {
        try {
            scanService.scan(scanService.defaultScanDays());
        } catch (Exception e) {
            log.error("每日质控扫描失败", e);
        }
    }
}
