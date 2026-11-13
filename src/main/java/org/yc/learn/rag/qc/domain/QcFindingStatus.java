package org.yc.learn.rag.qc.domain;

/**
 * 质控异常闭环状态。
 *
 * @author zhengyanchuang
 */
public enum QcFindingStatus {

    /** 新发现，待处理。 */
    OPEN,

    /** 已认领，处理中。 */
    ACK,

    /** 已处理完成。 */
    RESOLVED,

    /** 人工确认非问题，冷静期内不再重复生成。 */
    IGNORED
}
