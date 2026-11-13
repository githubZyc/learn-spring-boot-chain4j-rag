package org.yc.learn.rag.qc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启质控定时扫描。
 *
 * @author zhengyanchuang
 */
@Configuration
@EnableScheduling
public class QcSchedulingConfig {
}
