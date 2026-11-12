package org.yc.learn.rag.util;

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
 *   <li>禁止 DML / DDL / 注释 / 多语句；</li>
 *   <li>只允许访问指定表；</li>
 *   <li>外层强制套 {@code LIMIT 200}，即使模型自己写了更大的 LIMIT 也绕不过去。</li>
 * </ul>
 * 返回两个版本：{@code executableSql} 用于执行（带外层 LIMIT），
 * {@code displaySql} 用于前端展示（不带包装）。
 *
 * @author zhengyanchuang
 */
public final class SqlValidator {

    private static final int MAX_ROWS = 200;

    private static final Pattern TABLE_REF = Pattern.compile(
            "(?i)\\b(?:from|join)\\s+([`A-Za-z0-9_.]+)");

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

    public static ValidatedSql validateAndNormalize(String sql, String allowedTable) {
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

        Matcher matcher = TABLE_REF.matcher(cleaned);
        boolean foundTable = false;
        while (matcher.find()) {
            foundTable = true;
            String table = normalizeTableName(matcher.group(1));
            if (!table.equalsIgnoreCase(allowedTable)) {
                throw new IllegalArgumentException("只允许查询表：" + allowedTable);
            }
        }
        if (!foundTable) {
            throw new IllegalArgumentException("SQL 中没有识别到目标表");
        }

        String executable = "SELECT * FROM (" + cleaned + ") AS _rag_q LIMIT " + MAX_ROWS;
        return new ValidatedSql(executable, cleaned);
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
     * 校验结果：执行版与展示版。
     */
    public record ValidatedSql(String executableSql, String displaySql) {
    }
}
