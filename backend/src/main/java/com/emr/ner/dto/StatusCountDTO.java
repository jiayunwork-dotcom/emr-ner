package com.emr.ner.dto;

import lombok.Data;

@Data
public class StatusCountDTO {
    private Long pending = 0L;
    private Long processing = 0L;
    private Long completed = 0L;
    private Long annotated = 0L;
    private Long failed = 0L;
}
