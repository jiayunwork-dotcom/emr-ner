package com.emr.ner.dto;

import lombok.Data;
import java.util.List;

@Data
public class InferenceResultDTO {
    private String originalText;
    private List<EntityDTO> entities;
    private List<RelationDTO> relations;
    private List<TimelineDTO> timelines;
    private String modelVersion;
}
