package com.emr.ner.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TimelineDTO {
    private Long id;
    private String timeExpression;
    private LocalDate normalizedDate;
    private LocalDateTime normalizedDatetime;
    private String associatedEvent;
    private Long entityId;
    private Float confidence;
}
