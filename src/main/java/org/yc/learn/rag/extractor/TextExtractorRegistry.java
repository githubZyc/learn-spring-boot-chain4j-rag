package org.yc.learn.rag.extractor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文本提取策略的注册表，同时作为「系统支持哪些文件类型」的唯一来源。
 * <p>
 * Spring 会把所有 {@link DocumentTextExtractor} 实现注入构造函数，
 * 不需要手写工厂：新增格式自动注册，校验和解析共享同一份类型知识。
 *
 * @author zhengyanchuang
 */
@Component
public class TextExtractorRegistry {

    private final Map<String, DocumentTextExtractor> extractors;

    public TextExtractorRegistry(List<DocumentTextExtractor> extractors) {
        Map<String, DocumentTextExtractor> byType = new HashMap<>();
        for (DocumentTextExtractor extractor : extractors) {
            for (String type : extractor.supportedTypes()) {
                String key = normalizeType(type);
                DocumentTextExtractor previous = byType.put(key, extractor);
                if (previous != null) {
                    throw new IllegalStateException("文件类型重复注册：" + key);
                }
            }
        }
        this.extractors = Map.copyOf(byType);
    }

    /**
     * 是否支持该文件类型（传入扩展名，大小写不敏感）。
     */
    public boolean supports(String fileType) {
        return extractors.containsKey(normalizeType(fileType));
    }

    /**
     * 提取文件文本；文件类型不支持时抛 {@link IllegalArgumentException}。
     */
    public String extract(String fileType, Path path) throws IOException {
        DocumentTextExtractor extractor = extractors.get(normalizeType(fileType));
        if (extractor == null) {
            throw new IllegalArgumentException("不支持的文件类型：" + fileType);
        }
        return extractor.extract(path);
    }

    /**
     * 所有支持的类型（大写），例如 {@code [MD, PDF, TXT]}。
     */
    public Set<String> supportedTypes() {
        return extractors.keySet();
    }

    private static String normalizeType(String fileType) {
        return fileType == null ? "" : fileType.toUpperCase(Locale.ROOT);
    }
}
