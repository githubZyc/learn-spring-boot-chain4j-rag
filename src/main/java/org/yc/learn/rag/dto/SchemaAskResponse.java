package org.yc.learn.rag.dto;

import java.util.List;

/**
 * 「找表」响应。
 *
 * @author zhengyanchuang
 */
public record SchemaAskResponse(String question, List<SchemaMatchDto> matches) {

    public SchemaAskResponse {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
