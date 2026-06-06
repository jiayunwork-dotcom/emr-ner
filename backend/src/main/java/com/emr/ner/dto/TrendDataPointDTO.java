package com.emr.ner.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrendDataPointDTO {
    private LocalDateTime evaluatedAt;
    private Long taskId;
    private String modelVersionName;
    private Long modelVersionId;
    private Float f1Score;
    private Float precision;
    private Float recall;
    private Boolean isIncremental;
}
