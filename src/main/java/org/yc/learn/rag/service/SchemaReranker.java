package org.yc.learn.rag.service;

import org.yc.learn.rag.dto.SchemaMatchDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 「找表」结果的业务重排。
 * <p>
 * 向量相似度会把「明细表 / 共享表 / 备份表 / 日志表」排在主表前面，
 * 因为它们的描述同样包含业务关键词。这里做两件事：
 * <ul>
 *   <li>当用户没有明确问明细/共享/备份时，对专业化、附属类表降权；</li>
 *   <li>对标注了「主表」的结果加一点权重。</li>
 * </ul>
 * 降权是规则、可解释、可测试的，不引入额外模型调用；每条结果会带上 {@code rankReason}。
 *
 * @author zhengyanchuang
 */
public final class SchemaReranker {

    private static final List<String> SPECIALIZED_TERMS = List.of(
            "明细", "共享", "备份", "历史", "日志", "临时", "测试", "快照",
            "照片", "图片", "附件", "关联", "映射", "配置", "申请", "审批", "记录");

    private static final List<String> NAME_NOISE = List.of(
            "_bak", "_old", "_copy", "_history", "_tmp", "_test", "_backup", "_snapshot");

    private static final double SPECIALIZED_PENALTY = 0.04;
    private static final double MAIN_TABLE_BONUS = 0.01;

    private SchemaReranker() {
    }

    public static List<SchemaMatchDto> rerank(String question, List<SchemaMatchDto> matches, int limit) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }
        String lowerQuestion = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<SchemaMatchDto> ranked = new ArrayList<>(matches.size());

        for (SchemaMatchDto match : matches) {
            String text = (match.tableComment() + " " + match.tableName()).toLowerCase(Locale.ROOT);
            double score = match.vectorScore();
            List<String> reasons = new ArrayList<>();

            for (String term : SPECIALIZED_TERMS) {
                if (text.contains(term) && !lowerQuestion.contains(term)) {
                    score -= SPECIALIZED_PENALTY;
                    reasons.add("降权:" + term);
                }
            }
            for (String token : NAME_NOISE) {
                if (text.contains(token) && !lowerQuestion.contains(token)) {
                    score -= SPECIALIZED_PENALTY;
                    reasons.add("降权:" + token);
                }
            }
            if (text.contains("主表") && reasons.isEmpty()) {
                score += MAIN_TABLE_BONUS;
                reasons.add("主表优先");
            }

            score = Math.max(0, Math.min(1, score));
            ranked.add(match.withRank(score, String.join(",", reasons)));
        }

        return ranked.stream()
                .sorted(Comparator.comparingDouble(SchemaMatchDto::score).reversed()
                        .thenComparing(Comparator.comparingDouble(SchemaMatchDto::vectorScore).reversed())
                        .thenComparing(SchemaMatchDto::tableName))
                .limit(Math.max(1, limit))
                .toList();
    }
}
