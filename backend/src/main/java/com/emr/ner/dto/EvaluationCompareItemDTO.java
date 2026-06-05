package com.emr.ner.dto;

import lombok.Data;

@Data
public class EvaluationCompareItemDTO {
    private Long modelVersionId;
    private String modelVersionName;
    private java.time.LocalDateTime evaluatedAt;
    private java.util.Map<String, EvaluationMetricsDTO> metricsByType;
    private EvaluationMetricsDTO overallMacro;
    private EvaluationMetricsDTO overallMicro;
}
