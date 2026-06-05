package com.emr.ner.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvaluationResultDTO {
    private Long taskId;
    private Long datasetId;
    private Long modelVersionId;
    private String modelVersionName;
    private List<EvaluationMetricsDTO> metricsByType;
    private Map<String, Object> overall;
}
