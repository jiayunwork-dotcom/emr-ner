package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "relations")
public class Relation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "head_entity_id", nullable = false)
    private Long headEntityId;

    @Column(name = "tail_entity_id", nullable = false)
    private Long tailEntityId;

    @Column(name = "relation_type", nullable = false, length = 50)
    private String relationType;

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
