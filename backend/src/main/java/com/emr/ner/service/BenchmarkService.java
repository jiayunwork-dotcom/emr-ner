package com.emr.ner.service;

import com.emr.ner.dto.*;
import com.emr.ner.entity.*;
import com.emr.ner.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BenchmarkService {

    private final BenchmarkConfigRepository benchmarkConfigRepository;
    private final EvaluationDatasetRepository datasetRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final EvaluationTaskRepository taskRepository;
    private final EvaluationResultRepository resultRepository;
    private final EvaluationService evaluationService;
    private final EvaluationAsyncService evaluationAsyncService;

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "disease", "symptom", "drug", "test", "operation", "anatomy", "time"
    );

    public BenchmarkConfig getActiveBenchmark() {
        return benchmarkConfigRepository.findByIsActiveTrue().orElse(null);
    }

    public List<BenchmarkConfig> getAllBenchmarks() {
        return benchmarkConfigRepository.findAll();
    }

    @Transactional
    public BenchmarkConfig createBenchmark(BenchmarkConfigDTO configDTO, Long userId) {
        EvaluationDataset dataset = datasetRepository.findById(configDTO.getDatasetId())
            .orElseThrow(() -> new RuntimeException("数据集不存在: " + configDTO.getDatasetId()));

        if (Boolean.TRUE.equals(configDTO.getIsActive())) {
            benchmarkConfigRepository.findByIsActiveTrue().ifPresent(existing -> {
                existing.setIsActive(false);
                existing.setUpdatedAt(LocalDateTime.now());
                benchmarkConfigRepository.save(existing);
            });
        }

        BenchmarkConfig config = new BenchmarkConfig();
        config.setDatasetId(configDTO.getDatasetId());
        config.setDatasetName(dataset.getDatasetName());
        config.setIsActive(configDTO.getIsActive() != null ? configDTO.getIsActive() : false);
        config.setOverallMicroF1Threshold(configDTO.getOverallMicroF1Threshold() != null ? 
            configDTO.getOverallMicroF1Threshold() : 0.85f);
        config.setOverallMacroF1Threshold(configDTO.getOverallMacroF1Threshold() != null ? 
            configDTO.getOverallMacroF1Threshold() : 0.80f);
        config.setPerTypeF1Threshold(configDTO.getPerTypeF1Threshold() != null ? 
            configDTO.getPerTypeF1Threshold() : 0.70f);
        config.setTypeSpecificThresholds(configDTO.getTypeSpecificThresholds());
        config.setCreatedBy(userId);
        config.setUpdatedAt(LocalDateTime.now());

        return benchmarkConfigRepository.save(config);
    }

    @Transactional
    public BenchmarkConfig updateBenchmark(Long id, BenchmarkConfigDTO configDTO, Long userId) {
        BenchmarkConfig config = benchmarkConfigRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("基准配置不存在: " + id));

        if (configDTO.getDatasetId() != null && !configDTO.getDatasetId().equals(config.getDatasetId())) {
            EvaluationDataset dataset = datasetRepository.findById(configDTO.getDatasetId())
                .orElseThrow(() -> new RuntimeException("数据集不存在: " + configDTO.getDatasetId()));
            config.setDatasetId(configDTO.getDatasetId());
            config.setDatasetName(dataset.getDatasetName());
        }

        if (Boolean.TRUE.equals(configDTO.getIsActive()) && !Boolean.TRUE.equals(config.getIsActive())) {
            benchmarkConfigRepository.findByIsActiveTrue().ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setIsActive(false);
                    existing.setUpdatedAt(LocalDateTime.now());
                    benchmarkConfigRepository.save(existing);
                }
            });
        }

        if (configDTO.getIsActive() != null) config.setIsActive(configDTO.getIsActive());
        if (configDTO.getOverallMicroF1Threshold() != null) 
            config.setOverallMicroF1Threshold(configDTO.getOverallMicroF1Threshold());
        if (configDTO.getOverallMacroF1Threshold() != null) 
            config.setOverallMacroF1Threshold(configDTO.getOverallMacroF1Threshold());
        if (configDTO.getPerTypeF1Threshold() != null) 
            config.setPerTypeF1Threshold(configDTO.getPerTypeF1Threshold());
        if (configDTO.getTypeSpecificThresholds() != null) 
            config.setTypeSpecificThresholds(configDTO.getTypeSpecificThresholds());
        
        config.setUpdatedAt(LocalDateTime.now());

        return benchmarkConfigRepository.save(config);
    }

    @Transactional
    public void deleteBenchmark(Long id) {
        benchmarkConfigRepository.deleteById(id);
    }

    @Transactional
    public EvaluationTask triggerAutoRegressionTest(Long modelVersionId) {
        BenchmarkConfig benchmark = getActiveBenchmark();
        if (benchmark == null) {
            log.info("没有激活的基准配置，跳过自动回归测试，modelVersionId={}", modelVersionId);
            return null;
        }

        log.info("开始自动回归测试，modelVersionId={}, datasetId={}", modelVersionId, benchmark.getDatasetId());

        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
            .orElseThrow(() -> new RuntimeException("模型版本不存在: " + modelVersionId));

        modelVersion.setValidationStatus("running");
        modelVersion.setUpdatedAt(LocalDateTime.now());
        modelVersionRepository.save(modelVersion);

        try {
            EvaluationTask task = evaluationService.submitEvaluationTask(
                benchmark.getDatasetId(), modelVersionId, null, false);
            log.info("自动回归测试任务已提交，taskId={}", task.getId());
            return task;
        } catch (Exception e) {
            log.error("触发自动回归测试失败，modelVersionId={}", modelVersionId, e);
            modelVersion.setValidationStatus("failed");
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            modelVersion.setValidationDetails(details);
            modelVersion.setUpdatedAt(LocalDateTime.now());
            modelVersionRepository.save(modelVersion);
            throw e;
        }
    }

    @Async("taskExecutor")
    @Transactional
    public void triggerAutoRegressionTestAsync(Long modelVersionId) {
        try {
            Thread.sleep(1000);
            triggerAutoRegressionTest(modelVersionId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("自动回归测试被中断", e);
        } catch (Exception e) {
            log.error("异步触发自动回归测试失败，modelVersionId={}", modelVersionId, e);
        }
    }

    @Transactional
    public ValidationResultDTO validateModelAgainstBenchmark(Long modelVersionId, Long taskId) {
        BenchmarkConfig benchmark = getActiveBenchmark();
        if (benchmark == null) {
            log.info("没有激活的基准配置，跳过验证，modelVersionId={}", modelVersionId);
            return null;
        }

        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
            .orElseThrow(() -> new RuntimeException("模型版本不存在: " + modelVersionId));

        List<EvaluationResult> results = resultRepository.findByTaskId(taskId);
        if (results.isEmpty()) {
            log.warn("评估结果不存在，跳过验证，taskId={}", taskId);
            modelVersion.setValidationStatus("failed");
            Map<String, Object> details = new HashMap<>();
            details.put("error", "评估结果不存在");
            modelVersion.setValidationDetails(details);
            modelVersion.setUpdatedAt(LocalDateTime.now());
            modelVersionRepository.save(modelVersion);
            return null;
        }

        Map<String, EvaluationMetricsDTO> metricsMap = new HashMap<>();
        int totalTp = 0, totalFp = 0, totalFn = 0;
        float sumF1 = 0f;
        int validTypeCount = 0;

        for (EvaluationResult r : results) {
            EvaluationMetricsDTO metrics = new EvaluationMetricsDTO();
            metrics.setEntityType(r.getEntityType());
            metrics.setTruePositives(r.getTruePositives());
            metrics.setFalsePositives(r.getFalsePositives());
            metrics.setFalseNegatives(r.getFalseNegatives());
            metrics.setPrecision(r.getPrecision());
            metrics.setRecall(r.getRecall());
            metrics.setF1Score(r.getF1Score());
            metricsMap.put(r.getEntityType(), metrics);

            totalTp += r.getTruePositives();
            totalFp += r.getFalsePositives();
            totalFn += r.getFalseNegatives();

            if (r.getTruePositives() + r.getFalseNegatives() > 0) {
                sumF1 += r.getF1Score();
                validTypeCount++;
            }
        }

        float microPrecision = (totalTp + totalFp) > 0 ? (float) totalTp / (totalTp + totalFp) : 0f;
        float microRecall = (totalTp + totalFn) > 0 ? (float) totalTp / (totalTp + totalFn) : 0f;
        float microF1 = (microPrecision + microRecall) > 0 
            ? 2 * microPrecision * microRecall / (microPrecision + microRecall) : 0f;
        float macroF1 = validTypeCount > 0 ? sumF1 / validTypeCount : 0f;

        ValidationResultDTO validationResult = new ValidationResultDTO();
        List<String> failedItems = new ArrayList<>();
        Map<String, Object> actualValues = new HashMap<>();
        Map<String, Object> thresholds = new HashMap<>();

        actualValues.put("overallMicroF1", microF1);
        thresholds.put("overallMicroF1", benchmark.getOverallMicroF1Threshold());
        if (microF1 < benchmark.getOverallMicroF1Threshold()) {
            failedItems.add(String.format("Overall Micro-F1: 实际值 %.4f，阈值 %.4f", 
                microF1, benchmark.getOverallMicroF1Threshold()));
        }

        actualValues.put("overallMacroF1", macroF1);
        thresholds.put("overallMacroF1", benchmark.getOverallMacroF1Threshold());
        if (macroF1 < benchmark.getOverallMacroF1Threshold()) {
            failedItems.add(String.format("Overall Macro-F1: 实际值 %.4f，阈值 %.4f", 
                macroF1, benchmark.getOverallMacroF1Threshold()));
        }

        Map<String, Float> typeThresholds = benchmark.getTypeSpecificThresholds();
        for (String entityType : VALID_ENTITY_TYPES) {
            EvaluationMetricsDTO metrics = metricsMap.get(entityType);
            if (metrics != null && metrics.getTruePositives() + metrics.getFalseNegatives() > 0) {
                float threshold = benchmark.getPerTypeF1Threshold();
                if (typeThresholds != null && typeThresholds.containsKey(entityType)) {
                    threshold = typeThresholds.get(entityType);
                }
                actualValues.put(entityType + "F1", metrics.getF1Score());
                thresholds.put(entityType + "F1", threshold);
                if (metrics.getF1Score() < threshold) {
                    failedItems.add(String.format("%s F1: 实际值 %.4f，阈值 %.4f", 
                        entityType, metrics.getF1Score(), threshold));
                }
            }
        }

        validationResult.setPassed(failedItems.isEmpty());
        validationResult.setFailedItems(failedItems);
        validationResult.setActualValues(actualValues);
        validationResult.setThresholds(thresholds);

        modelVersion.setValidationStatus(failedItems.isEmpty() ? "passed" : "failed");
        Map<String, Object> details = new HashMap<>();
        details.put("passed", failedItems.isEmpty());
        details.put("failedItems", failedItems);
        details.put("actualValues", actualValues);
        details.put("thresholds", thresholds);
        details.put("benchmarkId", benchmark.getId());
        details.put("taskId", taskId);
        modelVersion.setValidationDetails(details);
        modelVersion.setUpdatedAt(LocalDateTime.now());
        modelVersionRepository.save(modelVersion);

        return validationResult;
    }
}
