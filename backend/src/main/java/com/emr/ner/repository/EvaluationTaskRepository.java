package com.emr.ner.repository;

import com.emr.ner.entity.EvaluationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationTaskRepository extends JpaRepository<EvaluationTask, Long> {

    Optional<EvaluationTask> findByDatasetIdAndStatusIn(Long datasetId, List<String> statuses);

    List<EvaluationTask> findByDatasetIdAndStatus(Long datasetId, String status);

    List<EvaluationTask> findByDatasetIdOrderByCreatedAtDesc(Long datasetId);
}
