package com.emr.ner.repository;

import com.emr.ner.entity.BenchmarkConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BenchmarkConfigRepository extends JpaRepository<BenchmarkConfig, Long> {

    Optional<BenchmarkConfig> findByIsActiveTrue();

    Optional<BenchmarkConfig> findByDatasetId(Long datasetId);
}
