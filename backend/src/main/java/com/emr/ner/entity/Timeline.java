package com.emr.ner.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "timelines")
public class Timeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "time_expression", nullable = false, columnDefinition = "TEXT")
    private String timeExpression;

    @Column(name = "normalized_date")
    private LocalDate normalizedDate;

    @Column(name = "normalized_datetime")
    private LocalDateTime normalizedDatetime;

    @Column(name = "associated_event", columnDefinition = "TEXT")
    private String associatedEvent;

    @Column(name = "entity_id")
    private Long entityId;

    private Float confidence;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
