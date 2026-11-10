package org.yc.learn.rag.extractor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * txt / md 等纯文本文件，直接按 UTF-8 读取。
 *
 * @author zhengyanchuang
 */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    private static final Set<String> TYPES = Set.of("TXT", "MD");

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public String extract(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
