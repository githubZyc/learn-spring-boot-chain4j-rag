package org.yc.learn.rag.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author zhengyanchuang
 */
class TextExtractorRegistryTest {

    @Test
    void resolvesExtractorCaseInsensitively() throws IOException {
        TextExtractorRegistry registry = new TextExtractorRegistry(List.of(fake("PDF", "pdf-text")));

        assertThat(registry.supports("pdf")).isTrue();
        assertThat(registry.supports("PDF")).isTrue();
        assertThat(registry.supports("docx")).isFalse();
        assertThat(registry.extract("PdF", Path.of("ignored"))).isEqualTo("pdf-text");
    }

    @Test
    void rejectsUnknownType() {
        TextExtractorRegistry registry = new TextExtractorRegistry(List.of(fake("TXT", "text")));

        assertThatThrownBy(() -> registry.extract("docx", Path.of("ignored")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docx");
    }

    @Test
    void rejectsDuplicateRegistration() {
        List<DocumentTextExtractor> extractors = List.of(fake("TXT", "a"), fake("TXT", "b"));

        assertThatThrownBy(() -> new TextExtractorRegistry(extractors))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TXT");
    }

    @Test
    void plainTextExtractorReadsUtf8(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "你好，RAG");

        assertThat(new PlainTextExtractor().extract(file)).isEqualTo("你好，RAG");
    }

    private static DocumentTextExtractor fake(String type, String content) {
        return new DocumentTextExtractor() {
            @Override
            public Set<String> supportedTypes() {
                return Set.of(type);
            }

            @Override
            public String extract(Path path) {
                return content;
            }
        };
    }
}
