package com.emr.ner.controller;

import com.emr.ner.dto.*;
import com.emr.ner.entity.Document;
import com.emr.ner.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    public ResponseEntity<Page<Document>> getDocuments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(documentService.getDocuments(status, patientId, documentType, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Document> getDocument(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<InferenceResultDTO> getDocumentResult(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocumentResult(id));
    }

    @PostMapping
    public ResponseEntity<Document> createDocument(@Valid @RequestBody DocumentRequestDTO request) {
        Document doc = documentService.createDocument(request, 1L);
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<InferenceResultDTO> processDocument(@PathVariable Long id) {
        InferenceResultDTO result = documentService.processDocument(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/infer")
    public ResponseEntity<InferenceResultDTO> infer(@RequestBody Map<String, Object> request) {
        String text = (String) request.get("text");
        InferenceResultDTO result = documentService.getInferenceService().infer(text, null);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{documentId}/entities/{entityId}")
    public ResponseEntity<?> updateEntity(
            @PathVariable Long documentId,
            @PathVariable Long entityId,
            @RequestBody EntityDTO dto) {
        return ResponseEntity.ok(documentService.updateEntity(entityId, dto, 1L));
    }

    @PostMapping("/{documentId}/entities")
    public ResponseEntity<?> addEntity(
            @PathVariable Long documentId,
            @RequestBody EntityDTO dto) {
        return ResponseEntity.ok(documentService.addEntity(documentId, dto, 1L));
    }

    @DeleteMapping("/{documentId}/entities/{entityId}")
    public ResponseEntity<?> deleteEntity(
            @PathVariable Long documentId,
            @PathVariable Long entityId) {
        documentService.deleteEntity(entityId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{documentId}/relations/{relationId}")
    public ResponseEntity<?> updateRelation(
            @PathVariable Long documentId,
            @PathVariable Long relationId,
            @RequestBody RelationDTO dto) {
        return ResponseEntity.ok(documentService.updateRelation(relationId, dto, 1L));
    }

    @PostMapping("/{documentId}/relations")
    public ResponseEntity<?> addRelation(
            @PathVariable Long documentId,
            @RequestBody RelationDTO dto) {
        return ResponseEntity.ok(documentService.addRelation(documentId, dto, 1L));
    }

    @DeleteMapping("/{documentId}/relations/{relationId}")
    public ResponseEntity<?> deleteRelation(
            @PathVariable Long documentId,
            @PathVariable Long relationId) {
        documentService.deleteRelation(relationId);
        return ResponseEntity.ok().build();
    }
}
