package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "evaluation_results")
public class EvaluationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "model_version_id", nullable = false)
    private Long modelVersionId;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "true_positives")
    private Integer truePositives = 0;

    @Column(name = "false_positives")
    private Integer falsePositives = 0;

    @Column(name = "false_negatives")
    private Integer falseNegatives = 0;

    @Column(name = "precision")
    private Float precision;

    @Column(name = "recall")
    private Float recall;

    @Column(name = "f1_score")
    private Float f1Score;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
