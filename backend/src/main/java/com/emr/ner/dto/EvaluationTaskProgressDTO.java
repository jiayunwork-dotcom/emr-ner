package com.emr.ner.dto;

import lombok.Data;

@Data
public class EvaluationTaskProgressDTO {
    private Long taskId;
    private String status;
    private Integer totalCount;
    private Integer processedCount;
    private Integer failedCount;
    private String modelVersionName;
    private String errorMessage;
}
