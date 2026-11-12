package org.yc.learn.rag.connector.mysql;

import java.util.List;

/**
 * 一次只读查询的结果：列名 + 行数据（已按列顺序展开）。
 * <p>
 * 值统一转成字符串，{@code null} 保留为 {@code null}，由上层决定如何展示；
 * 二进制列不返回内容，避免把图片等大对象塞进响应。
 *
 * @author zhengyanchuang
 */
public record QueryResult(List<String> columns, List<List<String>> rows) {

    public QueryResult {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows;
    }
}
