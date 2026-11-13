package org.yc.learn.rag.qc.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yc.learn.rag.config.AppProperties;
import org.yc.learn.rag.dto.SqlQueryResponse;
import org.yc.learn.rag.exception.ApiException;
import org.yc.learn.rag.service.SqlQueryService;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 质控范围的自然语言问答。
 * <p>
 * 复用数据库知识库的 Schema RAG + NL2SQL，但把表范围限制在质控表，
 * 避免用户问出客户、营销等无关数据。
 *
 * @author zhengyanchuang
 */
@Service
public class QcAskService {

    private static final List<String> DEFAULT_TABLES = List.of(
            "hospital_surgical_qc_record",
            "hospital_surgical_qc_setup",
            "hospital_surgical_qc_anesthesia",
            "hospital_surgical_qc_extraction",
            "hospital_surgical_qc_planting",
            "hospital_surgical_qc_flow_def");

    private static final String GLOSSARY = """
            手术质控业务词典：
            - 已归档 = qc_status = 4
            - 进行中 = qc_status = 1
            - 已完成 = qc_status = 2
            - 异常终止 = qc_status = 3
            - 已失效 = qc_status IN (5, -5)
            - 应收数 = qc_status IN (1,2,3,4) AND del_flag = 0
            - 缺少麻醉 = 阶段 >= ANESTHESIA 且麻醉子表无记录
            - 长发植发 = transplant_technique LIKE '%2%'
            - 阶段顺序：SETUP < ANESTHESIA < EXTRACTION < PLANTING < FINISHED
            - 手术结束 / 已完成阶段 = current_stage = 'FINISHED'
            - 状态进行中但已手术结束 = qc_status = 1 AND current_stage = 'FINISHED'
            - 状态已完成 = qc_status = 2
            - 状态已归档 = qc_status = 4
            - 归档天数 = DATEDIFF(filed_time, surgery_date)，仅统计 qc_status = 4 且 filed_time 非空
            - 分院归档最慢 = 按 wise_depart_name 分组，按平均归档天数降序
            """;

    private final SqlQueryService sqlQueryService;
    private final AppProperties properties;

    public QcAskService(SqlQueryService sqlQueryService, AppProperties properties) {
        this.sqlQueryService = sqlQueryService;
        this.properties = properties;
    }

    public SqlQueryResponse ask(String question) {
        String enriched = GLOSSARY + "\n用户问题：" + question;
        try {
            return sqlQueryService.query(enriched, allowedTables());
        } catch (ApiException e) {
            if ("SCHEMA_TABLE_NOT_FOUND".equals(e.getCode())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "QC_NO_MATCHING_TABLE",
                        "没有找到与问题匹配的质控表，请换个问法");
            }
            throw e;
        }
    }

    public Set<String> allowedTables() {
        AppProperties.Qc qc = properties.qc();
        if (qc == null || qc.allowedTables() == null || qc.allowedTables().isBlank()) {
            return Set.copyOf(DEFAULT_TABLES);
        }
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        Arrays.stream(qc.allowedTables().split(","))
                .map(String::trim)
                .filter(table -> !table.isEmpty())
                .forEach(tables::add);
        return tables.isEmpty() ? Set.copyOf(DEFAULT_TABLES) : Set.copyOf(tables);
    }
}
