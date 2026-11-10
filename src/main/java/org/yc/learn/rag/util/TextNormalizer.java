package org.yc.learn.rag.util;

/**
 * 文本规范化：去 BOM、统一换行、判断空白。
 *
 * @author zhengyanchuang
 */
public final class TextNormalizer {

    private static final char BOM = '﻿';

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text;
        if (!normalized.isEmpty() && normalized.charAt(0) == BOM) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
