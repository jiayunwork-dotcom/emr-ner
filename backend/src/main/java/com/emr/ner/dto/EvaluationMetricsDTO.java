package com.emr.ner.dto;

import lombok.Data;

@Data
public class EvaluationMetricsDTO {
    private String entityType;
    private Integer truePositives;
    private Integer falsePositives;
    private Integer falseNegatives;
    private Float precision;
    private Float recall;
    private Float f1Score;
}
