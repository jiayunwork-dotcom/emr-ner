package com.emr.ner.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BenchmarkConfigDTO {
    private Long id;
    private Long datasetId;
    private String datasetName;
    private Boolean isActive;
    private Float overallMicroF1Threshold;
    private Float overallMacroF1Threshold;
    private Float perTypeF1Threshold;
    private Map<String, Float> typeSpecificThresholds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
