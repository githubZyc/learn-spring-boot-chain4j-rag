package org.yc.learn.rag.qc.rule;

import org.yc.learn.rag.qc.domain.QcCase;

import java.util.List;

/**
 * 手术质控规则扩展点。
 * <p>
 * 每条规则实现一个 Bean，由 Spring 注入 {@link QcRuleRegistry}；
 * 规则只做「病例 → 异常草稿」的纯判断，不直接读写数据库，便于单测。
 *
 * @author zhengyanchuang
 */
public interface QcRule {

    /**
     * 稳定规则编码，前端与审计依赖，不要随意修改。
     */
    String code();

    String name();

    QcSeverity severity();

    /**
     * 对一例手术执行规则；没有命中返回空列表。
     */
    List<QcFindingDraft> evaluate(QcCase qcCase);
}
