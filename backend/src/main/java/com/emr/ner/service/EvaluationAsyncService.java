package com.emr.ner.service;

import com.emr.ner.entity.EvaluationTask;
import com.emr.ner.repository.EvaluationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationAsyncService {

    private final EvaluationTaskRepository taskRepository;
    private final EvaluationCoreService evaluationCoreService;

    @Async("taskExecutor")
    @Transactional
    public void executeEvaluationAsync(Long taskId) {
        try {
            EvaluationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("任务不存在: " + taskId));
            
            task.setStatus("running");
            task.setStartedAt(java.time.LocalDateTime.now());
            taskRepository.save(task);

            evaluationCoreService.doEvaluate(task);

        } catch (Exception e) {
            log.error("评估任务执行失败", e);
            try {
                EvaluationTask task = taskRepository.findById(taskId).orElse(null);
                if (task != null) {
                    task.setStatus("failed");
                    task.setErrorMessage(e.getMessage());
                    task.setCompletedAt(java.time.LocalDateTime.now());
                    taskRepository.save(task);
                }
            } catch (Exception ex) {
                log.error("更新任务状态失败", ex);
            }
        }
    }
}
