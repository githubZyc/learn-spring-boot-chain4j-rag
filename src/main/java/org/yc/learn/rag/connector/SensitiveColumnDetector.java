package org.yc.learn.rag.connector;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 按列名识别敏感字段。
 * <p>
 * 只做「默认规则」，不是权限系统：列名规范化后（小写、去掉下划线和连字符）
 * 命中精确词或后缀时判定为敏感。业务可以通过配置追加列名，避免误判或漏判。
 * <p>
 * 刻意不使用裸后缀 {@code name}：{@code dept_name} 这类业务字段不应被脱敏，
 * 而 {@code patient_name} 通过精确词命中。
 *
 * @author zhengyanchuang
 */
public final class SensitiveColumnDetector {

    private static final Set<String> EXACT = Set.of(
            "name", "realname", "patientname", "fullname", "username",
            "idcard", "idnumber", "idno", "identityno", "certno",
            "phone", "phoneno", "mobile", "mobilephone", "tel", "telephone",
            "address", "homeaddress", "email",
            "diagnosis", "disease",
            // 国内 HIS 常见拼音命名
            "xm", "xingming", "sfzh", "shenfenzheng", "sjh", "shoujihao",
            "lianxidianhua", "dianhua", "juzhudi", "dizhi", "zhenduan");

    private static final List<String> SUFFIXES = List.of(
            "idcard", "idnumber", "phone", "mobile", "email", "address", "diagnosis",
            "xingming", "shenfenzheng", "lianxidianhua", "juzhudi", "zhenduan");

    private SensitiveColumnDetector() {
    }

    /**
     * @param columnName 列名
     * @param extra      业务自定义的敏感列名（大小写不敏感）
     */
    public static boolean isSensitive(String columnName, Set<String> extra) {
        String normalized = normalize(columnName);
        if (normalized.isEmpty()) {
            return false;
        }
        if (EXACT.contains(normalized)) {
            return true;
        }
        if (extra != null && extra.stream().map(SensitiveColumnDetector::normalize).anyMatch(normalized::equals)) {
            return true;
        }
        return SUFFIXES.stream().anyMatch(normalized::endsWith);
    }

    static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
