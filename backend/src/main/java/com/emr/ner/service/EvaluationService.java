package com.emr.ner.service;

import com.emr.ner.dto.*;
import com.emr.ner.entity.*;
import com.emr.ner.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final EvaluationAsyncService evaluationAsyncService;
    private final ObjectMapper objectMapper;

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "disease", "symptom", "drug", "test", "operation", "anatomy", "time"
    );

    public EvaluationTrendDTO getEvaluationTrends(Long datasetId) {
        EvaluationDataset dataset = getDataset(datasetId);
        
        List<EvaluationTask> allTasks = taskRepository.findByDatasetIdOrderByCreatedAtDesc(datasetId);
        List<EvaluationTask> completedTasks = allTasks.stream()
            .filter(t -> "completed".equals(t.getStatus()))
            .collect(Collectors.toList());

        Map<String, List<TrendDataPointDTO>> trendsByEntityType = new HashMap<>();
        for (String type : VALID_ENTITY_TYPES) {
            trendsByEntityType.put(type, new ArrayList<>());
        }

        List<TrendDataPointDTO> overallMicroTrend = new ArrayList<>();
        List<TrendDataPointDTO> overallMacroTrend = new ArrayList<>();

        Map<Long, String> modelVersionNames = new HashMap<>();

        for (EvaluationTask task : completedTasks) {
            List<EvaluationResult> results = resultRepository.findByTaskId(task.getId());
            if (results.isEmpty()) continue;

            if (!modelVersionNames.containsKey(task.getModelVersionId())) {
                modelVersionNames.put(task.getModelVersionId(), task.getModelVersionName());
            }

            Map<String, EvaluationResult> resultMap = new HashMap<>();
            int totalTp = 0, totalFp = 0, totalFn = 0;
            float sumF1 = 0f;
            int validTypeCount = 0;

            for (EvaluationResult r : results) {
                resultMap.put(r.getEntityType(), r);
                totalTp += r.getTruePositives();
                totalFp += r.getFalsePositives();
                totalFn += r.getFalseNegatives();

                if (r.getTruePositives() + r.getFalseNegatives() > 0) {
                    sumF1 += r.getF1Score();
                    validTypeCount++;
                }
            }

            for (String type : VALID_ENTITY_TYPES) {
                EvaluationResult r = resultMap.get(type);
                if (r != null) {
                    TrendDataPointDTO point = new TrendDataPointDTO();
                    point.setEvaluatedAt(task.getCompletedAt());
                    point.setTaskId(task.getId());
                    point.setModelVersionId(task.getModelVersionId());
                    point.setModelVersionName(task.getModelVersionName());
                    point.setF1Score(r.getF1Score());
                    point.setPrecision(r.getPrecision());
                    point.setRecall(r.getRecall());
                    point.setIsIncremental(task.getIsIncremental());
                    trendsByEntityType.get(type).add(point);
                }
            }

            float microPrecision = (totalTp + totalFp) > 0 ? (float) totalTp / (totalTp + totalFp) : 0f;
            float microRecall = (totalTp + totalFn) > 0 ? (float) totalTp / (totalTp + totalFn) : 0f;
            float microF1 = (microPrecision + microRecall) > 0 
                ? 2 * microPrecision * microRecall / (microPrecision + microRecall) : 0f;
            float macroF1 = validTypeCount > 0 ? sumF1 / validTypeCount : 0f;

            TrendDataPointDTO microPoint = new TrendDataPointDTO();
            microPoint.setEvaluatedAt(task.getCompletedAt());
            microPoint.setTaskId(task.getId());
            microPoint.setModelVersionId(task.getModelVersionId());
            microPoint.setModelVersionName(task.getModelVersionName());
            microPoint.setF1Score(microF1);
            microPoint.setPrecision(microPrecision);
            microPoint.setRecall(microRecall);
            microPoint.setIsIncremental(task.getIsIncremental());
            overallMicroTrend.add(microPoint);

            TrendDataPointDTO macroPoint = new TrendDataPointDTO();
            macroPoint.setEvaluatedAt(task.getCompletedAt());
            macroPoint.setTaskId(task.getId());
            macroPoint.setModelVersionId(task.getModelVersionId());
            macroPoint.setModelVersionName(task.getModelVersionName());
            macroPoint.setF1Score(macroF1);
            macroPoint.setIsIncremental(task.getIsIncremental());
            overallMacroTrend.add(macroPoint);
        }

        for (List<TrendDataPointDTO> points : trendsByEntityType.values()) {
            points.sort(Comparator.comparing(TrendDataPointDTO::getEvaluatedAt));
        }
        overallMicroTrend.sort(Comparator.comparing(TrendDataPointDTO::getEvaluatedAt));
        overallMacroTrend.sort(Comparator.comparing(TrendDataPointDTO::getEvaluatedAt));

        EvaluationTrendDTO trendDTO = new EvaluationTrendDTO();
        trendDTO.setDatasetId(datasetId);
        trendDTO.setDatasetName(dataset.getDatasetName());
        trendDTO.setTrendsByEntityType(trendsByEntityType);
        trendDTO.setOverallMicroTrend(overallMicroTrend);
        trendDTO.setOverallMacroTrend(overallMacroTrend);

        return trendDTO;
    }

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

    private String calculateContentHash(List<Map<String, Object>> content) {
        try {
            String contentStr = objectMapper.writeValueAsString(content);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(contentStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("计算内容哈希失败", e);
            return null;
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
        dataset.setContentHash(calculateContentHash(data));
        dataset.setCreatedBy(userId);
        dataset.setUpdatedAt(LocalDateTime.now());

        return datasetRepository.save(dataset);
    }

    public List<EvaluationDataset> getAllDatasets() {
        return datasetRepository.findAll();
    }

    public EvaluationDataset getDataset(Long id) {
        return datasetRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("评估数据集不存在: " + id));
    }

    public Map<String, Object> checkIncrementalAvailability(Long datasetId, Long modelVersionId) {
        Map<String, Object> result = new HashMap<>();
        result.put("canIncrement", false);
        result.put("reason", "");
        result.put("lastEvaluatedCount", 0);
        result.put("currentCount", 0);
        result.put("newRecordsCount", 0);

        EvaluationDataset dataset = getDataset(datasetId);
        int currentCount = dataset.getRecordCount();
        result.put("currentCount", currentCount);

        List<EvaluationTask> completedTasks = taskRepository
            .findByDatasetIdAndModelVersionIdOrderByCreatedAtDesc(datasetId, modelVersionId);

        List<EvaluationTask> validTasks = completedTasks.stream()
            .filter(t -> "completed".equals(t.getStatus()))
            .collect(Collectors.toList());

        if (validTasks.isEmpty()) {
            result.put("reason", "该模型版本尚未在此数据集上进行过评估");
            log.info("增量评估检查：无已完成任务，datasetId={}, modelVersionId={}", datasetId, modelVersionId);
            return result;
        }

        EvaluationTask lastTask = validTasks.get(0);
        int lastEndIndex = lastTask.getEndIndex() != null ? lastTask.getEndIndex() : 
                          (lastTask.getTotalCount() != null ? lastTask.getTotalCount() : 0);
        result.put("lastEvaluatedCount", lastEndIndex);

        log.info("增量评估检查：datasetId={}, modelVersionId={}, currentCount={}, lastTaskId={}, lastEndIndex={}",
            datasetId, modelVersionId, currentCount, lastTask.getId(), lastEndIndex);

        if (currentCount < lastEndIndex) {
            result.put("reason", "数据集内容已变更，需要全量重新评估");
            result.put("contentChanged", true);
            log.info("增量评估检查：记录数减少，判定为内容变更");
            return result;
        }

        if (currentCount == lastEndIndex) {
            result.put("reason", "数据集没有新增记录");
            log.info("增量评估检查：记录数未变，无新增");
            return result;
        }

        if (lastEndIndex <= 0) {
            result.put("reason", "历史评估数据不完整，需要全量重新评估");
            log.info("增量评估检查：历史数据不完整，lastEndIndex={}", lastEndIndex);
            return result;
        }

        result.put("canIncrement", true);
        result.put("startIndex", lastEndIndex);
        result.put("newRecordsCount", currentCount - lastEndIndex);
        result.put("baseTaskId", lastTask.getId());

        log.info("增量评估检查：可以增量评估，新增{}条记录，从索引{}开始", 
            currentCount - lastEndIndex, lastEndIndex);

        return result;
    }

    @Transactional
    public EvaluationTask submitEvaluationTask(Long datasetId, Long modelVersionId, Long userId, boolean isIncremental) {
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
        task.setIsIncremental(false);
        task.setStartIndex(0);
        task.setEndIndex(dataset.getRecordCount());

        if (isIncremental) {
            Map<String, Object> incrementalCheck = checkIncrementalAvailability(datasetId, modelVersionId);
            if (!(Boolean) incrementalCheck.get("canIncrement")) {
                throw new IllegalArgumentException((String) incrementalCheck.get("reason"));
            }
            task.setIsIncremental(true);
            task.setStartIndex((Integer) incrementalCheck.get("startIndex"));
            task.setBaseTaskId((Long) incrementalCheck.get("baseTaskId"));
        }

        task = taskRepository.save(task);

        evaluationAsyncService.executeEvaluationAsync(task.getId());

        return task;
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
