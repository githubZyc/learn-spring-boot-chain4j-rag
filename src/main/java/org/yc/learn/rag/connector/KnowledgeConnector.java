package org.yc.learn.rag.connector;

/**
 * 外部数据源 → 文档的接入扩展点（SPI）。
 * <p>
 * 与 {@code TextExtractorRegistry} 同构：每种数据源实现一个 Bean，由 Spring 自动注入
 * {@link KnowledgeConnectorRegistry}。新增数据源只需要新增实现类，
 * 不允许在同步 / 入库流程里按数据源类型写 {@code if / switch}。
 * <p>
 * 连接器只负责「外部数据 → {@link RawDocument}」，规范化、切分、向量化复用现有入库链路。
 *
 * @author zhengyanchuang
 */
public interface KnowledgeConnector {

    /**
     * 数据源类型标识，小写，例如 {@code mysql}。
     */
    String type();

    /**
     * 校验连接配置是否可用；不抛出异常，失败信息直接面向用户展示。
     */
    ConnectorTestResult test(ConnectorConfig config);

    /**
     * 按游标拉取一页数据。
     *
     * @param config 数据源连接与字段映射配置
     * @param cursor 上一页返回的游标；首次同步传 {@code null}
     * @param limit  本页最大条数
     */
    ConnectorBatch fetch(ConnectorConfig config, String cursor, int limit);
}
