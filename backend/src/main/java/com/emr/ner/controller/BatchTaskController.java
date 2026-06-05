package com.emr.ner.controller;

import com.emr.ner.dto.DocumentRequestDTO;
import com.emr.ner.entity.BatchTask;
import com.emr.ner.service.BatchTaskService;
import com.emr.ner.service.DocumentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/batch-tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BatchTaskController {

    private final BatchTaskService batchTaskService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<BatchTask>> getTasks(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(batchTaskService.getTasks(status, null, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BatchTask> getTask(@PathVariable Long id) {
        return ResponseEntity.ok(batchTaskService.getTaskById(id));
    }

    @PostMapping("/upload")
    public ResponseEntity<BatchTask> uploadAndProcess(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String modelVersion,
            @RequestParam("file") MultipartFile file) throws Exception {
        
        BatchTask task = batchTaskService.createTask(
            taskName != null ? taskName : file.getOriginalFilename(),
            modelVersion,
            1L
        );

        List<DocumentRequestDTO> documents;
        String filename = file.getOriginalFilename();
        
        if (filename != null && filename.endsWith(".zip")) {
            documents = parseZipFile(file.getInputStream());
        } else if (filename != null && filename.endsWith(".json")) {
            documents = parseJsonFile(file.getInputStream());
        } else {
            throw new IllegalArgumentException("不支持的文件格式，请上传JSON或ZIP文件");
        }

        documentService.processBatchTask(task.getId(), documents, 1L);
        
        return ResponseEntity.ok(task);
    }

    @PostMapping("/json")
    public ResponseEntity<BatchTask> processJson(
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String modelVersion,
            @RequestBody List<DocumentRequestDTO> documents) {
        
        BatchTask task = batchTaskService.createTask(
            taskName != null ? taskName : "批量处理任务",
            modelVersion,
            1L
        );

        documentService.processBatchTask(task.getId(), documents, 1L);
        
        return ResponseEntity.ok(task);
    }

    private List<DocumentRequestDTO> parseJsonFile(InputStream inputStream) throws Exception {
        return objectMapper.readValue(inputStream, new TypeReference<List<DocumentRequestDTO>>() {});
    }

    private List<DocumentRequestDTO> parseZipFile(InputStream inputStream) throws Exception {
        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry;
        List<DocumentRequestDTO> allDocuments = new java.util.ArrayList<>();
        
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
                List<DocumentRequestDTO> docs = objectMapper.readValue(zis, new TypeReference<List<DocumentRequestDTO>>() {});
                allDocuments.addAll(docs);
            }
        }
        zis.close();
        
        return allDocuments;
    }
}
