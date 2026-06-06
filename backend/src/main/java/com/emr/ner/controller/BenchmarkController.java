package com.emr.ner.controller;

import com.emr.ner.dto.BenchmarkConfigDTO;
import com.emr.ner.entity.BenchmarkConfig;
import com.emr.ner.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/benchmark")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @GetMapping
    public ResponseEntity<List<BenchmarkConfig>> getAllBenchmarks() {
        return ResponseEntity.ok(benchmarkService.getAllBenchmarks());
    }

    @GetMapping("/active")
    public ResponseEntity<BenchmarkConfig> getActiveBenchmark() {
        BenchmarkConfig config = benchmarkService.getActiveBenchmark();
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    @PostMapping
    public ResponseEntity<?> createBenchmark(@RequestBody BenchmarkConfigDTO configDTO) {
        try {
            BenchmarkConfig config = benchmarkService.createBenchmark(configDTO, null);
            return ResponseEntity.ok(config);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBenchmark(
            @PathVariable Long id,
            @RequestBody BenchmarkConfigDTO configDTO) {
        try {
            BenchmarkConfig config = benchmarkService.updateBenchmark(id, configDTO, null);
            return ResponseEntity.ok(config);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBenchmark(@PathVariable Long id) {
        try {
            benchmarkService.deleteBenchmark(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "删除失败: " + e.getMessage()));
        }
    }

    @PostMapping("/validate/{modelVersionId}")
    public ResponseEntity<?> validateModel(@PathVariable Long modelVersionId) {
        try {
            var result = benchmarkService.triggerAutoRegressionTest(modelVersionId);
            return ResponseEntity.ok(Map.of("taskId", result != null ? result.getId() : null, "message", "验证任务已启动"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
