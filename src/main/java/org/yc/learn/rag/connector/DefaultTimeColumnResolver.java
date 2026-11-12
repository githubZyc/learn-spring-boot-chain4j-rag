package org.yc.learn.rag.connector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推断一张表用于「今天 / 最近 N 天」的业务时间字段。
 * <p>
 * 优先创建时间，其次更新时间；都没有时退化为「名字含 time/date 的时间类型列」。
 * 推断不确定是常态，因此上层允许按表配置覆盖，缺失时应当反问用户而不是猜。
 *
 * @author zhengyanchuang
 */
public final class DefaultTimeColumnResolver {

    private static final List<String> CREATED = List.of(
            "createtime", "createdat", "createdate", "createdon", "gmtcreate", "inserttime", "insertat");

    private static final List<String> UPDATED = List.of(
            "updatetime", "updatedat", "modifiedon", "gmtmodified", "modifytime", "modifiedat");

    private DefaultTimeColumnResolver() {
    }

    /**
     * @return 原始列名（保留大小写）；没有合适列时返回 {@code null}
     */
    public static String resolve(List<SchemaColumn> columns) {
        Map<String, SchemaColumn> byNormalized = columns.stream()
                .collect(Collectors.toMap(
                        column -> normalize(column.name()),
                        Function.identity(),
                        (first, ignored) -> first));

        for (String candidate : CREATED) {
            SchemaColumn column = byNormalized.get(candidate);
            if (column != null) {
                return column.name();
            }
        }
        for (String candidate : UPDATED) {
            SchemaColumn column = byNormalized.get(candidate);
            if (column != null) {
                return column.name();
            }
        }
        for (SchemaColumn column : columns) {
            String normalized = normalize(column.name());
            if (isTemporal(column.type()) && (normalized.contains("time") || normalized.contains("date"))) {
                return column.name();
            }
        }
        return null;
    }

    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static boolean isTemporal(String type) {
        if (type == null) {
            return false;
        }
        String upper = type.toUpperCase(Locale.ROOT);
        return upper.startsWith("DATETIME") || upper.startsWith("TIMESTAMP") || upper.startsWith("DATE");
    }
}
