package com.emr.ner.service;

import com.emr.ner.dto.*;
import com.emr.ner.entity.*;
import com.emr.ner.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationDatasetRepository datasetRepository;
    private final EvaluationTaskRepository taskRepository;
    private final EvaluationResultRepository resultRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final InferenceService inferenceService;
    private final ObjectMapper objectMapper;

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "disease", "symptom", "drug", "test", "operation", "anatomy", "time"
    );

    public List<DatasetValidationErrorDTO> validateDataset(List<Map<String, Object>> data) {
        List<DatasetValidationErrorDTO> errors = new ArrayList<>();

        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> record = data.get(i);
            
            if (!record.containsKey("text")) {
                errors.add(new DatasetValidationErrorDTO(i, null, "缺少text字段"));
                continue;
            }
            if (!(record.get("text") instanceof String)) {
                errors.add(new DatasetValidationErrorDTO(i, null, "text字段必须是字符串"));
                continue;
            }
            if (!record.containsKey("entities")) {
                errors.add(new DatasetValidationErrorDTO(i, null, "缺少entities字段"));
                continue;
            }
            if (!(record.get("entities") instanceof List)) {
                errors.add(new DatasetValidationErrorDTO(i, null, "entities字段必须是数组"));
                continue;
            }

            String text = (String) record.get("text");
            List<Map<String, Object>> entities = (List<Map<String, Object>>) record.get("entities");

            for (int j = 0; j < entities.size(); j++) {
                Map<String, Object> entity = entities.get(j);
                validateEntity(i, j, entity, text, errors);
            }
        }

        return errors;
    }

    private void validateEntity(int recordIdx, int entityIdx, Map<String, Object> entity, 
                                String text, List<DatasetValidationErrorDTO> errors) {
        if (!entity.containsKey("text")) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, "实体缺少text字段"));
            return;
        }
        if (!entity.containsKey("type")) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, "实体缺少type字段"));
            return;
        }
        if (!entity.containsKey("start")) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, "实体缺少start字段"));
            return;
        }
        if (!entity.containsKey("end")) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, "实体缺少end字段"));
            return;
        }

        String type = (String) entity.get("type");
        if (!VALID_ENTITY_TYPES.contains(type)) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, 
                "不支持的实体类型: " + type + "，支持的类型: " + VALID_ENTITY_TYPES));
        }

        Object startObj = entity.get("start");
        Object endObj = entity.get("end");
        
        if (!(startObj instanceof Number) || !(endObj instanceof Number)) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, "start和end必须是数字"));
            return;
        }

        int start = ((Number) startObj).intValue();
        int end = ((Number) endObj).intValue();

        if (start >= end) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, 
                "start必须小于end，当前: start=" + start + ", end=" + end));
        }
        if (start < 0 || end > text.length()) {
            errors.add(new DatasetValidationErrorDTO(recordIdx, entityIdx, 
                "实体位置超出文本范围，文本长度: " + text.length() + "，实体位置: [" + start + ", " + end + ")"));
        }
    }

    @Transactional
    public EvaluationDataset uploadDataset(String datasetName, String description, 
                                           MultipartFile file, Long userId) throws Exception {
        String contentStr = new String(file.getBytes());
        List<Map<String, Object>> data = objectMapper.readValue(contentStr, 
            new TypeReference<List<Map<String, Object>>>() {});

        List<DatasetValidationErrorDTO> errors = validateDataset(data);
        if (!errors.isEmpty()) {
            String errorMsg = "数据集格式校验失败: " + 
                errors.stream()
                    .map(e -> String.format("第%d条记录%s: %s", 
                        e.getRecordIndex() + 1,
                        e.getEntityIndex() != null ? "第" + (e.getEntityIndex() + 1) + "个实体" : "",
                        e.getMessage()))
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException(errorMsg);
        }

        EvaluationDataset dataset = new EvaluationDataset();
        dataset.setDatasetName(datasetName);
        dataset.setDescription(description);
        dataset.setRecordCount(data.size());
        dataset.setContent(data);
        dataset.setCreatedBy(userId);

        return datasetRepository.save(dataset);
    }

    public List<EvaluationDataset> getAllDatasets() {
        return datasetRepository.findAll();
    }

    public EvaluationDataset getDataset(Long id) {
        return datasetRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("评估数据集不存在: " + id));
    }

    @Transactional
    public EvaluationTask submitEvaluationTask(Long datasetId, Long modelVersionId, Long userId) {
        Optional<EvaluationTask> runningTask = taskRepository
            .findByDatasetIdAndStatusIn(datasetId, List.of("running", "pending"));
        
        if (runningTask.isPresent()) {
            EvaluationTask task = runningTask.get();
            throw new IllegalStateException(
                "当前数据集正在评估中，评估模型版本: " + task.getModelVersionName() +
                "，任务ID: " + task.getId());
        }

        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
            .orElseThrow(() -> new RuntimeException("模型版本不存在: " + modelVersionId));

        EvaluationDataset dataset = getDataset(datasetId);

        EvaluationTask task = new EvaluationTask();
        task.setDatasetId(datasetId);
        task.setModelVersionId(modelVersionId);
        task.setModelVersionName(modelVersion.getVersionName());
        task.setStatus("pending");
        task.setTotalCount(dataset.getRecordCount());
        task.setCreatedBy(userId);

        task = taskRepository.save(task);

        executeEvaluationAsync(task.getId());

        return task;
    }

    @Async("taskExecutor")
    @Transactional
    public void executeEvaluationAsync(Long taskId) {
        try {
            EvaluationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
            
            task.setStatus("running");
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);

            EvaluationDataset dataset = getDataset(task.getDatasetId());
            List<Map<String, Object>> records = dataset.getContent();

            Map<String, int[]> typeCounts = new HashMap<>();
            for (String type : VALID_ENTITY_TYPES) {
                typeCounts.put(type, new int[]{0, 0, 0});
            }

            int processed = 0;
            int failed = 0;

            for (Map<String, Object> record : records) {
                try {
                    String text = (String) record.get("text");
                    List<Map<String, Object>> goldEntities = (List<Map<String, Object>>) record.get("entities");

                    InferenceResultDTO inferenceResult = inferenceService.infer(text, null);
                    List<EntityDTO> predEntities = inferenceResult.getEntities();

                    compareEntities(goldEntities, predEntities, typeCounts);

                    processed++;
                    if (processed % 10 == 0) {
                        task.setProcessedCount(processed);
                        taskRepository.save(task);
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("评估记录处理失败: {}", e.getMessage());
                }
            }

            saveEvaluationResults(task.getId(), task.getModelVersionId(), task.getDatasetId(), typeCounts);

            task.setStatus("completed");
            task.setProcessedCount(processed);
            task.setFailedCount(failed);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

        } catch (Exception e) {
            log.error("评估任务执行失败", e);
            try {
                EvaluationTask task = taskRepository.findById(taskId).orElse(null);
                if (task != null) {
                    task.setStatus("failed");
                    task.setErrorMessage(e.getMessage());
                    task.setCompletedAt(LocalDateTime.now());
                    taskRepository.save(task);
                }
            } catch (Exception ex) {
                log.error("更新任务状态失败", ex);
            }
        }
    }

    private void compareEntities(List<Map<String, Object>> goldEntities, 
                                  List<EntityDTO> predEntities,
                                  Map<String, int[]> typeCounts) {
        Set<String> goldSet = new HashSet<>();
        Map<String, String> goldTypeMap = new HashMap<>();
        
        for (Map<String, Object> gold : goldEntities) {
            String type = (String) gold.get("type");
            int start = ((Number) gold.get("start")).intValue();
            int end = ((Number) gold.get("end")).intValue();
            String text = (String) gold.get("text");
            String key = start + "-" + end + "-" + text + "-" + type;
            goldSet.add(key);
            goldTypeMap.put(key, type);
        }

        Set<String> predSet = new HashSet<>();
        Map<String, String> predTypeMap = new HashMap<>();
        
        for (EntityDTO pred : predEntities) {
            String type = pred.getEntityType();
            if (!VALID_ENTITY_TYPES.contains(type)) continue;
            int start = pred.getStartPos();
            int end = pred.getEndPos();
            String text = pred.getEntityText();
            String key = start + "-" + end + "-" + text + "-" + type;
            predSet.add(key);
            predTypeMap.put(key, type);
        }

        for (String key : goldSet) {
            String type = goldTypeMap.get(key);
            int[] counts = typeCounts.get(type);
            if (predSet.contains(key)) {
                counts[0]++;
            } else {
                counts[2]++;
            }
        }

        for (String key : predSet) {
            if (!goldSet.contains(key)) {
                String type = predTypeMap.get(key);
                int[] counts = typeCounts.get(type);
                counts[1]++;
            }
        }
    }

    private void saveEvaluationResults(Long taskId, Long modelVersionId, Long datasetId,
                                        Map<String, int[]> typeCounts) {
        for (Map.Entry<String, int[]> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            int[] counts = entry.getValue();
            int tp = counts[0];
            int fp = counts[1];
            int fn = counts[2];

            EvaluationResult result = new EvaluationResult();
            result.setTaskId(taskId);
            result.setModelVersionId(modelVersionId);
            result.setDatasetId(datasetId);
            result.setEntityType(type);
            result.setTruePositives(tp);
            result.setFalsePositives(fp);
            result.setFalseNegatives(fn);

            float precision = (tp + fp) > 0 ? (float) tp / (tp + fp) : 0f;
            float recall = (tp + fn) > 0 ? (float) tp / (tp + fn) : 0f;
            float f1 = (precision + recall) > 0 ? 2 * precision * recall / (precision + recall) : 0f;

            result.setPrecision(precision);
            result.setRecall(recall);
            result.setF1Score(f1);

            resultRepository.save(result);
        }
    }

    public EvaluationTaskProgressDTO getTaskProgress(Long taskId) {
        EvaluationTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));

        EvaluationTaskProgressDTO dto = new EvaluationTaskProgressDTO();
        dto.setTaskId(task.getId());
        dto.setStatus(task.getStatus());
        dto.setTotalCount(task.getTotalCount());
        dto.setProcessedCount(task.getProcessedCount());
        dto.setFailedCount(task.getFailedCount());
        dto.setModelVersionName(task.getModelVersionName());
        dto.setErrorMessage(task.getErrorMessage());
        return dto;
    }

    public List<EvaluationCompareItemDTO> getComparisonResults(Long datasetId) {
        List<EvaluationTask> completedTasks = taskRepository
            .findByDatasetIdAndStatus(datasetId, "completed");

        Set<Long> processedModelIds = new HashSet<>();
        List<EvaluationCompareItemDTO> results = new ArrayList<>();

        for (int i = completedTasks.size() - 1; i >= 0; i--) {
            EvaluationTask task = completedTasks.get(i);
            if (processedModelIds.contains(task.getModelVersionId())) {
                continue;
            }
            processedModelIds.add(task.getModelVersionId());

            List<EvaluationResult> typeResults = resultRepository.findByTaskId(task.getId());
            
            EvaluationCompareItemDTO item = new EvaluationCompareItemDTO();
            item.setModelVersionId(task.getModelVersionId());
            item.setModelVersionName(task.getModelVersionName());
            item.setEvaluatedAt(task.getCompletedAt());

            Map<String, EvaluationMetricsDTO> metricsMap = new HashMap<>();
            int totalTp = 0, totalFp = 0, totalFn = 0;
            float sumF1 = 0f;
            int validTypeCount = 0;

            for (EvaluationResult r : typeResults) {
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

            item.setMetricsByType(metricsMap);

            EvaluationMetricsDTO macroOverall = new EvaluationMetricsDTO();
            macroOverall.setEntityType("overall-macro");
            macroOverall.setF1Score(validTypeCount > 0 ? sumF1 / validTypeCount : 0f);
            item.setOverallMacro(macroOverall);

            EvaluationMetricsDTO microOverall = new EvaluationMetricsDTO();
            microOverall.setEntityType("overall-micro");
            microOverall.setTruePositives(totalTp);
            microOverall.setFalsePositives(totalFp);
            microOverall.setFalseNegatives(totalFn);
            float microPrecision = (totalTp + totalFp) > 0 ? (float) totalTp / (totalTp + totalFp) : 0f;
            float microRecall = (totalTp + totalFn) > 0 ? (float) totalTp / (totalTp + totalFn) : 0f;
            float microF1 = (microPrecision + microRecall) > 0 
                ? 2 * microPrecision * microRecall / (microPrecision + microRecall) : 0f;
            microOverall.setPrecision(microPrecision);
            microOverall.setRecall(microRecall);
            microOverall.setF1Score(microF1);
            item.setOverallMicro(microOverall);

            results.add(item);
        }

        return results;
    }

    @Transactional
    public void deleteDataset(Long datasetId) {
        resultRepository.deleteAll(
            resultRepository.findByDatasetId(datasetId)
        );
        taskRepository.deleteAll(
            taskRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId)
        );
        datasetRepository.deleteById(datasetId);
    }
}
