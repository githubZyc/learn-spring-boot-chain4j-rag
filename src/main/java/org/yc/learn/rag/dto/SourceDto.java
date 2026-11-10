package org.yc.learn.rag.dto;

/**
 * 检索到的引用来源。
 *
 * @param index      本次检索结果中的序号（从 1 开始）
 * @param docId      文档 ID
 * @param fileName   文件名
 * @param chunkIndex chunk 在文档中的序号
 * @param score      相似度分数
 * @param snippet    片段预览
 * @author zhengyanchuang
 */
public record SourceDto(
        int index,
        String docId,
        String fileName,
        Integer chunkIndex,
        double score,
        String snippet) {
}
