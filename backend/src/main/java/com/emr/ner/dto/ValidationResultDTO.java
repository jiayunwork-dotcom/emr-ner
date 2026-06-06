package com.emr.ner.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ValidationResultDTO {
    private boolean passed;
    private List<String> failedItems;
    private Map<String, Object> actualValues;
    private Map<String, Object> thresholds;
}
