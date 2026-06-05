package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "batch_tasks")
public class BatchTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", length = 255)
    private String taskName;

    @Column(length = 20)
    private String status = "pending";

    @Column(name = "total_count")
    private Integer totalCount = 0;

    @Column(name = "processed_count")
    private Integer processedCount = 0;

    @Column(name = "failed_count")
    private Integer failedCount = 0;

    @Column(name = "input_file_path", length = 255)
    private String inputFilePath;

    @Column(name = "output_file_path", length = 255)
    private String outputFilePath;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
