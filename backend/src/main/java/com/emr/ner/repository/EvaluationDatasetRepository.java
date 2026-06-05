package com.emr.ner.repository;

import com.emr.ner.entity.EvaluationDataset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EvaluationDatasetRepository extends JpaRepository<EvaluationDataset, Long> {
}
