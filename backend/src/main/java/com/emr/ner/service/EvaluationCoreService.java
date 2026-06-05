package com.emr.ner.service;

import com.emr.ner.dto.EntityDTO;
import com.emr.ner.dto.InferenceResultDTO;
import com.emr.ner.entity.EvaluationDataset;
import com.emr.ner.entity.EvaluationResult;
import com.emr.ner.entity.EvaluationTask;
import com.emr.ner.repository.EvaluationDatasetRepository;
import com.emr.ner.repository.EvaluationResultRepository;
import com.emr.ner.repository.EvaluationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationCoreService {

    private final EvaluationDatasetRepository datasetRepository;
    private final EvaluationTaskRepository taskRepository;
    private final EvaluationResultRepository resultRepository;
    private final InferenceService inferenceService;

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "disease", "symptom", "drug", "test", "operation", "anatomy", "time"
    );

    @Transactional
    public void doEvaluate(EvaluationTask task) {
        EvaluationDataset dataset = datasetRepository.findById(task.getDatasetId())
            .orElseThrow(() -> new RuntimeException("数据集不存在: " + task.getDatasetId()));
        
        List<Map<String, Object>> records = dataset.getContent();

        Map<String, int[]> typeCounts = new HashMap<>();
        for (String type : VALID_ENTITY_TYPES) {
            typeCounts.put(type, new int[]{0, 0, 0});
        }

        int processed = 0;
        int failed = 0;

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            try {
                String text = (String) record.get("text");
                List<Map<String, Object>> goldEntities = (List<Map<String, Object>>) record.get("entities");

                InferenceResultDTO inferenceResult = inferenceService.infer(text, null);
                List<EntityDTO> predEntities = inferenceResult.getEntities();

                compareEntities(goldEntities, predEntities, typeCounts, text, i);

                processed++;
                if (processed % 10 == 0 || processed == records.size()) {
                    task.setProcessedCount(processed);
                    taskRepository.save(task);
                }
            } catch (Exception e) {
                failed++;
                log.warn("第{}条记录评估处理失败: {}", i + 1, e.getMessage());
            }
        }

        saveEvaluationResults(task.getId(), task.getModelVersionId(), task.getDatasetId(), typeCounts);

        task.setStatus("completed");
        task.setProcessedCount(processed);
        task.setFailedCount(failed);
        task.setCompletedAt(java.time.LocalDateTime.now());
        taskRepository.save(task);
    }

    private void compareEntities(List<Map<String, Object>> goldEntities, 
                                  List<EntityDTO> predEntities,
                                  Map<String, int[]> typeCounts,
                                  String text, int recordIndex) {
        Set<String> goldSet = new HashSet<>();
        Map<String, String> goldTypeMap = new HashMap<>();
        
        for (Map<String, Object> gold : goldEntities) {
            String type = (String) gold.get("type");
            int start = ((Number) gold.get("start")).intValue();
            String goldText = (String) gold.get("text");
            
            String key = start + "-" + goldText + "-" + type;
            goldSet.add(key);
            goldTypeMap.put(key, type);
        }

        Set<String> predSet = new HashSet<>();
        Map<String, String> predTypeMap = new HashMap<>();
        
        for (EntityDTO pred : predEntities) {
            String type = pred.getEntityType();
            if (!VALID_ENTITY_TYPES.contains(type)) continue;
            int start = pred.getStartPos();
            String predText = pred.getEntityText();
            
            String key = start + "-" + predText + "-" + type;
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
}
