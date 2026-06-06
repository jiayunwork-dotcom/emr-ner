package com.emr.ner.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class EvaluationTrendDTO {
    private Long datasetId;
    private String datasetName;
    private Map<String, List<TrendDataPointDTO>> trendsByEntityType;
    private List<TrendDataPointDTO> overallMicroTrend;
    private List<TrendDataPointDTO> overallMacroTrend;
}
