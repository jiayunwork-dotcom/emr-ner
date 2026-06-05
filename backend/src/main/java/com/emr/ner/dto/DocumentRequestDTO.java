package com.emr.ner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DocumentRequestDTO {
    @NotBlank(message = "文档类型不能为空")
    private String documentType;
    
    private String title;
    
    @NotBlank(message = "文档内容不能为空")
    private String content;
    
    private String patientId;
    private String visitId;
    private LocalDate admissionDate;
    private LocalDate dischargeDate;
    private LocalDate referenceDate;
}
