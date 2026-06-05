package com.emr.ner.dto;

import lombok.Data;

@Data
public class ExportEntityDTO {
    private String text;
    private String type;
    private Integer start;
    private Integer end;
}
