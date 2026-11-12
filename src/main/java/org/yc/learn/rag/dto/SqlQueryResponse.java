package org.yc.learn.rag.dto;

import java.util.List;

/**
 * 自然语言查数结果。
 *
 * @author zhengyanchuang
 */
public record SqlQueryResponse(
        String question,
        List<SqlTableRefDto> tables,
        String sql,
        List<String> columns,
        List<List<String>> rows,
        int rowCount,
        long costMs,
        List<String> maskedColumns) {

    public SqlQueryResponse {
        tables = tables == null ? List.of() : List.copyOf(tables);
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows;
        maskedColumns = maskedColumns == null ? List.of() : List.copyOf(maskedColumns);
    }
}
