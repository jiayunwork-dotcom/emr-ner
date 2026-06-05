package com.emr.ner.repository;

import com.emr.ner.entity.BatchTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchTaskRepository extends JpaRepository<BatchTask, Long> {

    Page<BatchTask> findByStatus(String status, Pageable pageable);

    Page<BatchTask> findBySubmittedBy(Long userId, Pageable pageable);
}
