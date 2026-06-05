package com.emr.ner.dto;

import lombok.Data;

@Data
public class RelationDTO {
    private Long id;
    private Long headEntityId;
    private Long tailEntityId;
    private String headEntityText;
    private String tailEntityText;
    private String relationType;
    private Float confidence;
    private String source;
}
