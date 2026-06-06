package com.emr.ner.controller;

import com.emr.ner.dto.*;
import com.emr.ner.entity.EvaluationDataset;
import com.emr.ner.entity.EvaluationTask;
import com.emr.ner.service.EvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/evaluation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/datasets/upload")
    public ResponseEntity<?> uploadDataset(
            @RequestParam String datasetName,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file) {
        try {
            EvaluationDataset dataset = evaluationService.uploadDataset(
                datasetName, description, file, null);
            return ResponseEntity.ok(dataset);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/datasets")
    public ResponseEntity<List<EvaluationDataset>> getDatasets() {
        return ResponseEntity.ok(evaluationService.getAllDatasets());
    }

    @GetMapping("/datasets/{id}")
    public ResponseEntity<EvaluationDataset> getDataset(@PathVariable Long id) {
        return ResponseEntity.ok(evaluationService.getDataset(id));
    }

    @DeleteMapping("/datasets/{id}")
    public ResponseEntity<?> deleteDataset(@PathVariable Long id) {
        try {
            evaluationService.deleteDataset(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    @GetMapping("/datasets/{datasetId}/incremental-check/{modelVersionId}")
    public ResponseEntity<?> checkIncrementalAvailability(
            @PathVariable Long datasetId,
            @PathVariable Long modelVersionId) {
        try {
            Map<String, Object> result = evaluationService.checkIncrementalAvailability(
                datasetId, modelVersionId);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/datasets/{datasetId}/evaluate/{modelVersionId}")
    public ResponseEntity<?> submitEvaluation(
            @PathVariable Long datasetId,
            @PathVariable Long modelVersionId,
            @RequestParam(defaultValue = "false") boolean incremental) {
        try {
            EvaluationTask task = evaluationService.submitEvaluationTask(
                datasetId, modelVersionId, null, incremental);
            return ResponseEntity.ok(Map.of("taskId", task.getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/{taskId}/progress")
    public ResponseEntity<EvaluationTaskProgressDTO> getTaskProgress(@PathVariable Long taskId) {
        return ResponseEntity.ok(evaluationService.getTaskProgress(taskId));
    }

    @GetMapping("/datasets/{datasetId}/compare")
    public ResponseEntity<List<EvaluationCompareItemDTO>> getComparisonResults(
            @PathVariable Long datasetId) {
        return ResponseEntity.ok(evaluationService.getComparisonResults(datasetId));
    }

    @GetMapping("/datasets/{datasetId}/trends")
    public ResponseEntity<?> getEvaluationTrends(@PathVariable Long datasetId) {
        try {
            return ResponseEntity.ok(evaluationService.getEvaluationTrends(datasetId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/datasets/validate")
    public ResponseEntity<?> validateDataset(@RequestParam("file") MultipartFile file) {
        try {
            String contentStr = new String(file.getBytes());
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> data = mapper.readValue(contentStr, 
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            
            List<DatasetValidationErrorDTO> errors = evaluationService.validateDataset(data);
            
            if (errors.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", true, "recordCount", data.size()));
            } else {
                return ResponseEntity.ok(Map.of("valid", false, "errors", errors));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "JSON解析失败: " + e.getMessage()));
        }
    }
}
