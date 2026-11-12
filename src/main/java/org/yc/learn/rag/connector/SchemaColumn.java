package org.yc.learn.rag.connector;

/**
 * 数据表的一列，来源是数据库元数据。
 *
 * @author zhengyanchuang
 */
public record SchemaColumn(
        String name,
        String type,
        boolean nullable,
        boolean primaryKey,
        String comment,
        String defaultValue) {

    public SchemaColumn {
        comment = comment == null ? "" : comment.trim();
        defaultValue = defaultValue == null ? "" : defaultValue;
    }
}
