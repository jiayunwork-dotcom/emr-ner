package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "patient_id", length = 100)
    private String patientId;

    @Column(name = "visit_id", length = 100)
    private String visitId;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(length = 20)
    private String status = "pending";

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "annotated_by")
    private Long annotatedBy;

    @Column(name = "annotated_at")
    private LocalDateTime annotatedAt;
}
