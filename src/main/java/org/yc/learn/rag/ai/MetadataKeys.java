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

    /** 知识类型：document / schema */
    public static final String SOURCE_TYPE = "sourceType";
    /** Schema 知识库的 sourceType 取值 */
    public static final String SOURCE_SCHEMA = "schema";
    /** Schema 所属数据库 */
    public static final String SCHEMA_DATABASE = "schemaDatabase";
    /** 表名 */
    public static final String TABLE_NAME = "tableName";
    /** 表中文名 / 注释 / 业务别名 */
    public static final String TABLE_COMMENT = "tableComment";
    /** 默认时间字段 */
    public static final String DEFAULT_TIME_COLUMN = "defaultTimeColumn";
    /** 敏感字段，逗号分隔 */
    public static final String SENSITIVE_COLUMNS = "sensitiveColumns";

    private MetadataKeys() {
    }
}
