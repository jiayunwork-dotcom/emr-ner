package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "benchmark_configs")
public class BenchmarkConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "dataset_name", length = 255)
    private String datasetName;

    @Column(name = "is_active")
    private Boolean isActive = false;

    @Column(name = "overall_micro_f1_threshold")
    private Float overallMicroF1Threshold = 0.85f;

    @Column(name = "overall_macro_f1_threshold")
    private Float overallMacroF1Threshold = 0.80f;

    @Column(name = "per_type_f1_threshold")
    private Float perTypeF1Threshold = 0.70f;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_specific_thresholds", columnDefinition = "jsonb")
    private Map<String, Float> typeSpecificThresholds;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
