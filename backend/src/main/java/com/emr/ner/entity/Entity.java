package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "entities")
public class Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "entity_text", nullable = false, columnDefinition = "TEXT")
    private String entityText;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "start_pos", nullable = false)
    private Integer startPos;

    @Column(name = "end_pos", nullable = false)
    private Integer endPos;

    @Column(name = "is_negated")
    private Boolean isNegated = false;

    @Column(name = "is_uncertain")
    private Boolean isUncertain = false;

    private Float confidence;

    @Column(length = 20)
    private String source = "model";

    @Column(name = "annotated_by")
    private Long annotatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
