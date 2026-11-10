package org.yc.learn.rag.domain;

/**
 * 文档入库状态。
 *
 * @author zhengyanchuang
 */
public enum DocumentStatus {
    /** 已保存文件，等待处理 */
    PENDING,
    /** 正在解析 / 向量化 */
    PROCESSING,
    /** 已完成入库，可被检索 */
    READY,
    /** 入库失败，可重新索引 */
    FAILED
}
