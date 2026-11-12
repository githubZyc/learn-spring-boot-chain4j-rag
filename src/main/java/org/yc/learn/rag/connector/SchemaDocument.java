package org.yc.learn.rag.connector;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 一张表的完整结构描述，也是写入向量库的最小单位。
 * <p>
 * 刻意保持「一表一文档」：找表要的是表级结果，如果把字段切成多个 chunk，
 * 检索会返回「某个字段像」而不是「这张表就是你要的」，反而降低命中率。
 * <p>
 * {@link #toEmbeddingText()} 产出的文本会直接交给 embedding 模型，
 * 因此表名、中文注释、字段注释都要保留，中文注释是召回质量的关键。
 *
 * @author zhengyanchuang
 */
public record SchemaDocument(
        String database,
        String tableName,
        String tableComment,
        String primaryKey,
        List<SchemaColumn> columns,
        List<SchemaIndex> indexes,
        String defaultTimeColumn,
        List<String> sensitiveColumns) {

    public SchemaDocument {
        tableComment = tableComment == null ? "" : tableComment.trim();
        primaryKey = primaryKey == null ? "" : primaryKey;
        columns = columns == null ? List.of() : List.copyOf(columns);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        defaultTimeColumn = defaultTimeColumn == null ? "" : defaultTimeColumn;
        sensitiveColumns = sensitiveColumns == null ? List.of() : List.copyOf(sensitiveColumns);
    }

    /**
     * 渲染为用于 embedding 的纯文本。
     */
    public String toEmbeddingText() {
        StringBuilder text = new StringBuilder();
        text.append("表名: ").append(tableName).append('\n');
        if (!tableComment.isBlank()) {
            text.append("中文名: ").append(tableComment).append('\n');
        }
        text.append("数据库: ").append(database).append('\n');
        if (!primaryKey.isBlank()) {
            text.append("主键: ").append(primaryKey).append('\n');
        }
        if (!defaultTimeColumn.isBlank()) {
            text.append("默认时间字段: ").append(defaultTimeColumn).append('\n');
        }

        text.append("字段:\n");
        for (SchemaColumn column : columns) {
            text.append("- ").append(column.name()).append(' ').append(column.type());
            if (column.primaryKey()) {
                text.append(" 主键");
            }
            if (!column.comment().isBlank()) {
                text.append("，").append(column.comment());
            }
            text.append('\n');
        }

        if (!indexes.isEmpty()) {
            text.append("索引: ")
                    .append(indexes.stream().map(SchemaIndex::toText).collect(Collectors.joining(", ")))
                    .append('\n');
        }
        if (!sensitiveColumns.isEmpty()) {
            text.append("敏感字段: ").append(String.join(", ", sensitiveColumns)).append('\n');
        }
        return text.toString();
    }
}
