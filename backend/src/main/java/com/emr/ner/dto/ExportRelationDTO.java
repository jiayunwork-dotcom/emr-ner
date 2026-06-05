package com.emr.ner.dto;

import lombok.Data;

@Data
public class ExportRelationDTO {
    private Integer head;
    private Integer tail;
    private String type;
}
