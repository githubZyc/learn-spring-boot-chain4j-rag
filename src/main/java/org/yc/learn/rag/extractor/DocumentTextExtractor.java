package org.yc.learn.rag.extractor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * 文件类型 → 纯文本的提取策略。
 * <p>
 * 每种文件格式实现一个 Bean，由 Spring 自动注入 {@link TextExtractorRegistry}；
 * 新增格式只需要新增实现类，不需要改动入库流程。
 *
 * @author zhengyanchuang
 */
public interface DocumentTextExtractor {

    /**
     * 支持的文件类型，使用大写扩展名，例如 {@code PDF}。
     */
    Set<String> supportedTypes();

    /**
     * 读取文件并提取其中的文本；只负责「字节 → 原始文本」，不做换行 / BOM 规范化。
     */
    String extract(Path path) throws IOException;
}
