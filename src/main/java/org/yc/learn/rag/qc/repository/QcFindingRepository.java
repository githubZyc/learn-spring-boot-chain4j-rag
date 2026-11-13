package org.yc.learn.rag.qc.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.yc.learn.rag.qc.domain.QcFindingEntity;
import org.yc.learn.rag.qc.domain.QcFindingStatus;
import org.yc.learn.rag.qc.rule.QcSeverity;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 质控异常数据访问。
 *
 * @author zhengyanchuang
 */
public interface QcFindingRepository extends JpaRepository<QcFindingEntity, Long>,
        JpaSpecificationExecutor<QcFindingEntity> {

    Optional<QcFindingEntity> findByRuleCodeAndRecordId(String ruleCode, long recordId);

    List<QcFindingEntity> findByRecordId(long recordId);

    List<QcFindingEntity> findByRecordIdIn(Collection<Long> recordIds);

    List<QcFindingEntity> findBySurgeryDateBetween(LocalDate from, LocalDate to);

    long countByStatusIn(Collection<QcFindingStatus> statuses);

    long countBySeverityAndStatusIn(QcSeverity severity, Collection<QcFindingStatus> statuses);

    List<QcFindingEntity> findTop10ByStatusInOrderByLastFoundAtDesc(Collection<QcFindingStatus> statuses);
}
