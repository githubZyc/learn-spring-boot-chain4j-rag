package org.yc.learn.rag.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NL2SQL 的安全护栏。
 * <p>
 * 模型生成的 SQL 不可信，必须经过这里才能执行：
 * <ul>
 *   <li>只允许单条 SELECT / WITH 查询；</li>
 *   <li>禁止 DML / DDL / 注释 / 多语句 / 逗号连表；</li>
 *   <li>只允许访问调用方传入的授权表集合，JOIN 的表数量不设上限；</li>
 *   <li>外层强制套 {@code LIMIT 200}，即使模型自己写了更大的 LIMIT 也绕不过去。</li>
 * </ul>
 * {@link #inspect(String)} 先做基础安全检查并抽出引用的表，调用方可以据此按需加载更多表结构，
 * 再调用 {@link #validateAndNormalize(String, Collection)} 做最终表校验。
 *
 * @author zhengyanchuang
 */
public final class SqlValidator {

    private static final int MAX_ROWS = 200;

    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([`A-Za-z0-9_.]+)");

    private static final Pattern COMMA_JOIN = Pattern.compile(
            "(?i)\\bfrom\\s+[^()]*?,");

    private static final Pattern COMMENT = Pattern.compile(
            "(--|/\\*|\\*/|(^|\\s)#)");

    private static final Set<String> ALLOWED_STARTS = Set.of("select", "with");

    private static final Set<String> FORBIDDEN = Set.of(
            "insert", "update", "delete", "replace", "merge",
            "drop", "alter", "truncate", "create", "rename",
            "grant", "revoke", "call", "execute", "exec",
            "into", "outfile", "dumpfile", "load_file",
            "sleep", "benchmark",
            "information_schema", "performance_schema", "mysql",
            "for update", "lock in share mode");

    private SqlValidator() {
    }

    /**
     * 基础安全检查，并返回 SQL 引用的表名（已去掉反引号与库名前缀）。
     * <p>
     * 这里不校验表是否授权，调用方可以先根据结果补充表结构，再做最终校验。
     */
    public static InspectedSql inspect(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("模型没有生成 SQL");
        }
        String cleaned = stripCodeFence(sql).trim();
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("模型没有生成有效 SQL");
        }
        if (cleaned.contains(";")) {
            throw new IllegalArgumentException("只允许单条 SQL 语句");
        }
        if (COMMENT.matcher(cleaned).find()) {
            throw new IllegalArgumentException("SQL 中不允许注释");
        }
        if (COMMA_JOIN.matcher(cleaned).find()) {
            throw new IllegalArgumentException("多表关联请使用 JOIN 语法");
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        String firstWord = lower.split("\\s+", 2)[0];
        if (!ALLOWED_STARTS.contains(firstWord)) {
            throw new IllegalArgumentException("只允许 SELECT 查询");
        }
        for (String forbidden : FORBIDDEN) {
            if (Pattern.compile("(?s).*\\b" + Pattern.quote(forbidden) + "\\b.*").matcher(lower).matches()) {
                throw new IllegalArgumentException("SQL 包含禁止的关键字：" + forbidden);
            }
        }

        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REF.matcher(cleaned);
        while (matcher.find()) {
            tables.add(normalizeTableName(matcher.group(1)));
        }
        if (tables.isEmpty()) {
            throw new IllegalArgumentException("SQL 中没有识别到目标表");
        }
        return new InspectedSql(cleaned, Set.copyOf(tables));
    }

    /**
     * 在授权表集合内校验 SQL，并生成带外层 LIMIT 的执行版本。
     */
    public static ValidatedSql validateAndNormalize(String sql, Collection<String> allowedTables) {
        InspectedSql inspected = inspect(sql);
        Set<String> allowed = allowedTables.stream()
                .map(table -> table.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String table : inspected.tables()) {
            if (!allowed.contains(table.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("只允许查询已授权的表：" + table);
            }
        }
        return new ValidatedSql(wrap(inspected.cleanedSql()), inspected.cleanedSql());
    }

    /**
     * 单表便利重载，保留给只查一张表的调用方与测试。
     */
    public static ValidatedSql validateAndNormalize(String sql, String allowedTable) {
        return validateAndNormalize(sql, Set.of(allowedTable));
    }

    private static String wrap(String sql) {
        return "SELECT * FROM (" + sql + ") AS _rag_q LIMIT " + MAX_ROWS;
    }

    private static String stripCodeFence(String sql) {
        String trimmed = sql.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline >= 0) {
            trimmed = trimmed.substring(firstNewline + 1);
        }
        int lastFence = trimmed.lastIndexOf("```");
        if (lastFence >= 0) {
            trimmed = trimmed.substring(0, lastFence);
        }
        return trimmed.trim();
    }

    private static String normalizeTableName(String raw) {
        String table = raw.replace("`", "");
        int dot = table.lastIndexOf('.');
        return dot >= 0 ? table.substring(dot + 1) : table;
    }

    /**
     * 基础校验后的 SQL 与引用表集合。
     */
    public record InspectedSql(String cleanedSql, Set<String> tables) {
    }

    /**
     * 最终校验结果：执行版与展示版。
     */
    public record ValidatedSql(String executableSql, String displaySql) {
    }
}
