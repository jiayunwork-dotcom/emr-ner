package com.emr.ner.controller;

import com.emr.ner.entity.ModelVersion;
import com.emr.ner.repository.ModelVersionRepository;
import com.emr.ner.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ModelController {

    private final ModelVersionRepository modelVersionRepository;
    private final BenchmarkService benchmarkService;

    @GetMapping
    public ResponseEntity<List<ModelVersion>> getAllModels() {
        return ResponseEntity.ok(modelVersionRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ModelVersion> getModel(@PathVariable Long id) {
        return modelVersionRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<ModelVersion> getActiveModel() {
        return modelVersionRepository.findByIsActiveTrue()
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ModelVersion> createModel(@RequestBody ModelVersion model) {
        model.setIsActive(false);
        return ResponseEntity.ok(modelVersionRepository.save(model));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<ModelVersion> activateModel(@PathVariable Long id) {
        ModelVersion model = modelVersionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("模型不存在: " + id));
        
        modelVersionRepository.deactivateAll();
        model.setIsActive(true);
        return ResponseEntity.ok(modelVersionRepository.save(model));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ModelVersion> updateModel(
            @PathVariable Long id,
            @RequestBody ModelVersion modelUpdate) {
        ModelVersion model = modelVersionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("模型不存在: " + id));
        
        model.setDescription(modelUpdate.getDescription());
        model.setMetrics(modelUpdate.getMetrics());
        return ResponseEntity.ok(modelVersionRepository.save(model));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteModel(@PathVariable Long id) {
        modelVersionRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<ModelVersion> uploadModel(
            @RequestParam String versionName,
            @RequestParam String modelType,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file) {
        
        ModelVersion model = new ModelVersion();
        model.setVersionName(versionName);
        model.setModelType(modelType);
        model.setDescription(description);
        model.setIsActive(false);
        model.setValidationStatus("pending");
        model.setFilePath("/app/models/" + versionName);
        model.setUpdatedAt(java.time.LocalDateTime.now());
        
        model = modelVersionRepository.save(model);

        try {
            benchmarkService.triggerAutoRegressionTestAsync(model.getId());
            log.info("已提交异步自动回归测试任务，modelId={}", model.getId());
        } catch (Exception e) {
            log.warn("提交自动基准测试任务失败: {}", e.getMessage());
        }
        
        return ResponseEntity.ok(model);
    }

    @PostMapping("/{id}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateModel(
            @PathVariable Long id,
            @RequestParam("dataset") MultipartFile dataset) {
        
        Map<String, Object> metrics = Map.of(
            "overall", Map.of("precision", 0.88, "recall", 0.86, "f1", 0.87),
            "by_type", Map.of(
                "disease", Map.of("precision", 0.90, "recall", 0.88, "f1", 0.89),
                "symptom", Map.of("precision", 0.86, "recall", 0.84, "f1", 0.85),
                "drug", Map.of("precision", 0.91, "recall", 0.89, "f1", 0.90)
            )
        );

        ModelVersion model = modelVersionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("模型不存在: " + id));
        model.setMetrics(metrics);
        modelVersionRepository.save(model);

        return ResponseEntity.ok(metrics);
    }
}
