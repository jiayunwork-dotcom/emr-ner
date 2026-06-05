package com.emr.ner.dto;

import lombok.Data;
import java.util.List;

@Data
public class ExportDocumentDTO {
    private String text;
    private List<ExportEntityDTO> entities;
    private List<ExportRelationDTO> relations;
}
