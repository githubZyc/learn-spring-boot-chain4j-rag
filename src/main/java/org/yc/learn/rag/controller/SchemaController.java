package org.yc.learn.rag.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.yc.learn.rag.dto.SchemaAskRequest;
import org.yc.learn.rag.dto.SchemaAskResponse;
import org.yc.learn.rag.dto.SchemaStatusDto;
import org.yc.learn.rag.service.SchemaIndexService;
import org.yc.learn.rag.service.SchemaSearchService;

/**
 * 数据库知识库接口：同步表结构、查看状态、自然语言找表。
 *
 * @author zhengyanchuang
 */
@RestController
@RequestMapping("/api/schema")
public class SchemaController {

    private final SchemaIndexService schemaIndexService;
    private final SchemaSearchService schemaSearchService;

    public SchemaController(SchemaIndexService schemaIndexService,
                            SchemaSearchService schemaSearchService) {
        this.schemaIndexService = schemaIndexService;
        this.schemaSearchService = schemaSearchService;
    }

    /**
     * 触发后台同步，立即返回 RUNNING；前端轮询 {@code /status}。
     */
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SchemaStatusDto sync() {
        return schemaIndexService.submitSync();
    }

    @GetMapping("/status")
    public SchemaStatusDto status() {
        return schemaIndexService.status();
    }

    @PostMapping("/ask")
    public SchemaAskResponse ask(@Valid @RequestBody SchemaAskRequest request) {
        return new SchemaAskResponse(
                request.question(),
                schemaSearchService.search(request.question(), request.maxResults()));
    }
}
