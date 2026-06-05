package com.emr.ner.dto;

import lombok.Data;

@Data
public class EntityDTO {
    private Long id;
    private String entityText;
    private String entityType;
    private Integer startPos;
    private Integer endPos;
    private Boolean isNegated;
    private Boolean isUncertain;
    private Float confidence;
    private String source;
}
