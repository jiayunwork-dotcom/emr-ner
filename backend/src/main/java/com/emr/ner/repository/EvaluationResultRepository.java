package com.emr.ner.repository;

import com.emr.ner.entity.EvaluationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationResultRepository extends JpaRepository<EvaluationResult, Long> {

    List<EvaluationResult> findByDatasetIdAndModelVersionId(Long datasetId, Long modelVersionId);

    List<EvaluationResult> findByDatasetId(Long datasetId);

    List<EvaluationResult> findByTaskId(Long taskId);

    void deleteByTaskId(Long taskId);
}
