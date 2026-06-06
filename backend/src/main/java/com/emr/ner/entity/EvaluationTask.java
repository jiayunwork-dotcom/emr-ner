package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "evaluation_tasks")
public class EvaluationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dataset_id", nullable = false)
    private Long datasetId;

    @Column(name = "model_version_id", nullable = false)
    private Long modelVersionId;

    @Column(name = "model_version_name", length = 50)
    private String modelVersionName;

    @Column(length = 20)
    private String status = "pending";

    @Column(name = "total_count")
    private Integer totalCount = 0;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "failed_count")
    private Integer failedCount = 0;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "start_index")
    private Integer startIndex = 0;

    @Column(name = "end_index")
    private Integer endIndex = 0;

    @Column(name = "is_incremental")
    private Boolean isIncremental = false;

    @Column(name = "base_task_id")
    private Long baseTaskId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
