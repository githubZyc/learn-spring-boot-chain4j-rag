package org.yc.learn.rag.connector;

import java.util.List;

/**
 * 数据表的一个索引。
 *
 * @author zhengyanchuang
 */
public record SchemaIndex(String name, boolean unique, List<String> columns) {

    public SchemaIndex {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    /**
     * 用于拼进 Schema 文档的简短描述，例如 {@code idx_patient(patient_id)}。
     */
    public String toText() {
        return name + (unique ? "(唯一)" : "") + "(" + String.join(",", columns) + ")";
    }
}
