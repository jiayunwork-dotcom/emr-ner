package com.emr.ner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DatasetValidationErrorDTO {
    private Integer recordIndex;
    private Integer entityIndex;
    private String message;
}
