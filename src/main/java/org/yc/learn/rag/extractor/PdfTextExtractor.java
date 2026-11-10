package org.yc.learn.rag.extractor;

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/**
 * PDF 是二进制格式，按 UTF-8 直接读取会抛 {@code MalformedInputException}，
 * 这里交给 PDFBox 提取文字层。
 *
 * @author zhengyanchuang
 */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final Set<String> TYPES = Set.of("PDF");

    @Override
    public Set<String> supportedTypes() {
        return TYPES;
    }

    @Override
    public String extract(Path path) throws IOException {
        return FileSystemDocumentLoader
                .loadDocument(path, new ApachePdfBoxDocumentParser())
                .text();
    }
}
