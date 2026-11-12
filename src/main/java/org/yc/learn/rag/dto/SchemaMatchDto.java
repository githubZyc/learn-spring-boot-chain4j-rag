package org.yc.learn.rag.dto;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.yc.learn.rag.ai.MetadataKeys;

import java.util.Arrays;
import java.util.List;

/**
 * 「找表」返回的一张候选表。
 *
 * @author zhengyanchuang
 */
public record SchemaMatchDto(
        String tableName,
        String tableComment,
        String defaultTimeColumn,
        List<String> sensitiveColumns,
        double score,
        String snippet) {

    private static final int SNIPPET_MAX_LENGTH = 400;

    public static SchemaMatchDto from(EmbeddingMatch<TextSegment> match) {
        Metadata metadata = match.embedded().metadata();
        return new SchemaMatchDto(
                nullToEmpty(metadata.getString(MetadataKeys.TABLE_NAME)),
                nullToEmpty(metadata.getString(MetadataKeys.TABLE_COMMENT)),
                nullToEmpty(metadata.getString(MetadataKeys.DEFAULT_TIME_COLUMN)),
                splitCsv(metadata.getString(MetadataKeys.SENSITIVE_COLUMNS)),
                match.score() == null ? 0 : match.score(),
                snippet(match.embedded().text()));
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= SNIPPET_MAX_LENGTH ? text : text.substring(0, SNIPPET_MAX_LENGTH) + "…";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
