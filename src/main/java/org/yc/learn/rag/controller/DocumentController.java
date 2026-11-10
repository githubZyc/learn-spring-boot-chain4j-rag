package org.yc.learn.rag.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.yc.learn.rag.dto.DocumentDto;
import org.yc.learn.rag.service.DocumentService;

import java.util.List;

/**
 * 文档管理接口。
 *
 * @author zhengyanchuang
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传后立即返回 PENDING 记录，解析 / 向量化在后台执行；前端轮询列表获取进度。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentDto upload(@RequestParam("file") MultipartFile file) {
        return documentService.upload(file);
    }

    @GetMapping
    public List<DocumentDto> list() {
        return documentService.list();
    }

    @GetMapping("/{id}")
    public DocumentDto get(@PathVariable String id) {
        return documentService.get(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        documentService.delete(id);
    }

    @PostMapping("/{id}/reindex")
    public DocumentDto reindex(@PathVariable String id) {
        return documentService.reindex(id);
    }
}
