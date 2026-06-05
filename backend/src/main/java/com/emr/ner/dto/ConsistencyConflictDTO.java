package com.emr.ner.dto;

import lombok.Data;
import java.util.List;

@Data
public class ConsistencyConflictDTO {
    private String entityText;
    private List<EntityAnnotationDTO> annotations;
}
