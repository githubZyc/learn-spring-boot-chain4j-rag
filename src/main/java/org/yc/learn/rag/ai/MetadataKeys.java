package org.yc.learn.rag.ai;

/**
 * 写入 Qdrant payload 的元数据键名。
 * <p>
 * 键名不要包含 "."，Qdrant 会把点号视为嵌套路径。
 *
 * @author zhengyanchuang
 */
public final class MetadataKeys {

    /** 文档 ID，同时用于按文档删除向量 */
    public static final String DOC_ID = "docId";
    /** 原始文件名，用于引用展示 */
    public static final String FILE_NAME = "fileName";
    /** 切分器自动写入的 chunk 序号 */
    public static final String INDEX = "index";

    private MetadataKeys() {
    }
}
