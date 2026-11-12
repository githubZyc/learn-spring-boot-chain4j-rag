package org.yc.learn.rag.dto;

/**
 * 一次查询实际使用的表。
 *
 * @author zhengyanchuang
 */
public record SqlTableRefDto(String tableName, String tableComment) {

    public SqlTableRefDto {
        tableComment = tableComment == null ? "" : tableComment;
    }
}
