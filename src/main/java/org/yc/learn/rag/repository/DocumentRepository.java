package org.yc.learn.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.yc.learn.rag.domain.DocumentEntity;
import org.yc.learn.rag.domain.DocumentStatus;

import java.util.Collection;
import java.util.List;

/**
 * @author zhengyanchuang
 */
public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    List<DocumentEntity> findAllByOrderByCreatedAtDesc();

    List<DocumentEntity> findAllByStatusIn(Collection<DocumentStatus> statuses);
}
