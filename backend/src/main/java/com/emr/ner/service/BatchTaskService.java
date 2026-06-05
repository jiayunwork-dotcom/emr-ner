package com.emr.ner.service;

import com.emr.ner.entity.BatchTask;
import com.emr.ner.repository.BatchTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTaskService {

    private final BatchTaskRepository batchTaskRepository;

    public Page<BatchTask> getTasks(String status, Long userId, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return batchTaskRepository.findByStatus(status, pageable);
        }
        if (userId != null) {
            return batchTaskRepository.findBySubmittedBy(userId, pageable);
        }
        return batchTaskRepository.findAll(pageable);
    }

    public BatchTask getTaskById(Long id) {
        return batchTaskRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("任务不存在: " + id));
    }

    @Transactional
    public BatchTask createTask(String taskName, String modelVersion, Long userId) {
        BatchTask task = new BatchTask();
        task.setTaskName(taskName);
        task.setModelVersion(modelVersion);
        task.setSubmittedBy(userId);
        task.setStatus("pending");
        return batchTaskRepository.save(task);
    }

    @Transactional
    public void startTask(Long taskId, int totalCount) {
        BatchTask task = getTaskById(taskId);
        task.setStatus("running");
        task.setTotalCount(totalCount);
        task.setStartedAt(LocalDateTime.now());
        batchTaskRepository.save(task);
    }

    @Transactional
    public void updateProgress(Long taskId, int processedCount, int failedCount) {
        BatchTask task = getTaskById(taskId);
        task.setProcessedCount(processedCount);
        task.setFailedCount(failedCount);
        batchTaskRepository.save(task);
    }

    @Transactional
    public void completeTask(Long taskId, int processedCount, int failedCount) {
        BatchTask task = getTaskById(taskId);
        task.setStatus("completed");
        task.setProcessedCount(processedCount);
        task.setFailedCount(failedCount);
        task.setCompletedAt(LocalDateTime.now());
        batchTaskRepository.save(task);
    }

    @Transactional
    public void failTask(Long taskId, String errorMessage) {
        BatchTask task = getTaskById(taskId);
        task.setStatus("failed");
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(LocalDateTime.now());
        batchTaskRepository.save(task);
    }
}
