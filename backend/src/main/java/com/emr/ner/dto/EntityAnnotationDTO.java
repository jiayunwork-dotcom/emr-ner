package com.emr.ner.dto;

import lombok.Data;

@Data
public class EntityAnnotationDTO {
    private Long documentId;
    private String documentTitle;
    private Long entityId;
    private String entityType;
    private Integer startPos;
    private Integer endPos;
    private String source;
}
