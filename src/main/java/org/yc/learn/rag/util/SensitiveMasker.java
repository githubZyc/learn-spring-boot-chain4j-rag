package org.yc.learn.rag.util;

import java.util.Locale;

/**
 * 查询结果的敏感字段脱敏。
 * <p>
 * 只对 {@code SchemaDocument.sensitiveColumns()} 里标注的列调用；
 * 具体掩码规则按列名推断，推断不出时退化为全掩码 {@code ***}。
 *
 * @author zhengyanchuang
 */
public final class SensitiveMasker {

    private SensitiveMasker() {
    }

    public static String mask(String column, String value) {
        if (value == null) {
            return null;
        }
        String name = normalize(column);
        if (isIdCard(name)) {
            return maskIdCard(value);
        }
        if (isPhone(name)) {
            return maskPhone(value);
        }
        if (name.contains("email")) {
            return maskEmail(value);
        }
        if (isName(name)) {
            return maskName(value);
        }
        return "***";
    }

    private static boolean isName(String name) {
        return name.equals("name") || name.endsWith("name") || name.equals("xm")
                || name.endsWith("xingming");
    }

    private static boolean isIdCard(String name) {
        return name.contains("idcard") || name.contains("idno") || name.contains("certno")
                || name.contains("sfzh") || name.contains("identity")
                || name.contains("shenfenzheng");
    }

    private static boolean isPhone(String name) {
        return name.contains("phone") || name.contains("mobile") || name.equals("tel")
                || name.equals("telephone") || name.equals("sjh")
                || name.contains("lianxidianhua") || name.contains("dianhua")
                || name.contains("shoujihao");
    }

    private static String maskName(String value) {
        if (value.length() <= 1) {
            return "*";
        }
        return value.charAt(0) + "*".repeat(Math.min(2, value.length() - 1));
    }

    private static String maskIdCard(String value) {
        if (value.length() < 8) {
            return "***";
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 4);
    }

    private static String maskPhone(String value) {
        if (value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String maskEmail(String value) {
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static String normalize(String column) {
        return column == null ? "" : column.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }
}
