package com.emr.ner.service;

import com.emr.ner.dto.*;
import com.emr.ner.entity.Document;
import com.emr.ner.entity.Entity;
import com.emr.ner.entity.Relation;
import com.emr.ner.entity.Timeline;
import com.emr.ner.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EntityRepository entityRepository;
    private final RelationRepository relationRepository;
    private final TimelineRepository timelineRepository;
    private final InferenceService inferenceService;
    private final BatchTaskService batchTaskService;

    public InferenceService getInferenceService() {
        return inferenceService;
    }

    public Page<Document> getDocuments(String status, String patientId, String documentType, Pageable pageable) {
        if (status != null && !status.isEmpty()) {
            return documentRepository.findByStatus(status, pageable);
        }
        if (patientId != null && !patientId.isEmpty()) {
            return documentRepository.findByPatientId(patientId, pageable);
        }
        if (documentType != null && !documentType.isEmpty()) {
            return documentRepository.findByDocumentType(documentType, pageable);
        }
        return documentRepository.findAll(pageable);
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
    }

    public InferenceResultDTO getDocumentResult(Long id) {
        Document doc = getDocumentById(id);
        InferenceResultDTO result = new InferenceResultDTO();
        result.setOriginalText(doc.getContent());
        result.setModelVersion(doc.getModelVersion());
        
        List<Entity> entities = entityRepository.findByDocumentId(id);
        result.setEntities(entities.stream().map(this::toEntityDTO).collect(Collectors.toList()));
        
        List<Relation> relations = relationRepository.findByDocumentId(id);
        result.setRelations(relations.stream().map(r -> toRelationDTO(r, entities)).collect(Collectors.toList()));
        
        List<Timeline> timelines = timelineRepository.findByDocumentId(id);
        result.setTimelines(timelines.stream().map(this::toTimelineDTO).collect(Collectors.toList()));
        
        return result;
    }

    @Transactional
    public Document createDocument(DocumentRequestDTO request, Long userId) {
        Document doc = new Document();
        doc.setDocumentType(request.getDocumentType());
        doc.setTitle(request.getTitle());
        doc.setContent(request.getContent());
        doc.setPatientId(request.getPatientId());
        doc.setVisitId(request.getVisitId());
        doc.setAdmissionDate(request.getAdmissionDate());
        doc.setDischargeDate(request.getDischargeDate());
        doc.setReferenceDate(request.getReferenceDate());
        doc.setStatus("pending");
        doc.setCreatedBy(userId);
        
        doc = documentRepository.save(doc);
        return doc;
    }

    @Transactional
    public InferenceResultDTO processDocument(Long documentId) {
        Document doc = getDocumentById(documentId);
        doc.setStatus("processing");
        documentRepository.save(doc);

        try {
            InferenceResultDTO inferenceResult = inferenceService.infer(doc.getContent(), doc.getReferenceDate());
            
            entityRepository.deleteModelEntitiesByDocumentId(documentId);
            relationRepository.deleteModelRelationsByDocumentId(documentId);
            timelineRepository.deleteByDocumentId(documentId);

            List<Entity> savedEntities = new ArrayList<>();
            java.util.Map<Long, Long> entityIdMap = new java.util.HashMap<>();
            for (EntityDTO dto : inferenceResult.getEntities()) {
                Entity entity = new Entity();
                entity.setDocumentId(documentId);
                entity.setEntityText(dto.getEntityText());
                entity.setEntityType(dto.getEntityType());
                entity.setStartPos(dto.getStartPos());
                entity.setEndPos(dto.getEndPos());
                entity.setIsNegated(dto.getIsNegated());
                entity.setIsUncertain(dto.getIsUncertain());
                entity.setConfidence(dto.getConfidence());
                entity.setSource("model");
                entity = entityRepository.save(entity);
                savedEntities.add(entity);
                entityIdMap.put(dto.getId(), entity.getId());
                dto.setId(entity.getId());
            }

            for (RelationDTO dto : inferenceResult.getRelations()) {
                Relation relation = new Relation();
                relation.setDocumentId(documentId);
                Long newHeadId = entityIdMap.get(dto.getHeadEntityId());
                Long newTailId = entityIdMap.get(dto.getTailEntityId());
                if (newHeadId != null && newTailId != null) {
                    relation.setHeadEntityId(newHeadId);
                    relation.setTailEntityId(newTailId);
                    relation.setRelationType(dto.getRelationType());
                    relation.setConfidence(dto.getConfidence());
                    relation.setSource("model");
                    relation = relationRepository.save(relation);
                    dto.setId(relation.getId());
                    dto.setHeadEntityId(newHeadId);
                    dto.setTailEntityId(newTailId);
                }
            }

            for (TimelineDTO dto : inferenceResult.getTimelines()) {
                Timeline timeline = new Timeline();
                timeline.setDocumentId(documentId);
                timeline.setTimeExpression(dto.getTimeExpression());
                timeline.setNormalizedDate(dto.getNormalizedDate());
                timeline.setNormalizedDatetime(dto.getNormalizedDatetime());
                timeline.setAssociatedEvent(dto.getAssociatedEvent());
                if (dto.getEntityId() != null && entityIdMap.containsKey(dto.getEntityId())) {
                    timeline.setEntityId(entityIdMap.get(dto.getEntityId()));
                }
                timeline.setConfidence(dto.getConfidence());
                timeline = timelineRepository.save(timeline);
                dto.setId(timeline.getId());
            }

            doc.setStatus("completed");
            doc.setModelVersion(inferenceResult.getModelVersion());
            documentRepository.save(doc);

            return inferenceResult;
        } catch (Exception e) {
            log.error("处理文档失败: {}", documentId, e);
            doc.setStatus("failed");
            documentRepository.save(doc);
            throw new RuntimeException("处理文档失败: " + e.getMessage());
        }
    }

    @Transactional
    public Entity updateEntity(Long entityId, EntityDTO dto, Long userId) {
        Entity entity = entityRepository.findById(entityId)
            .orElseThrow(() -> new RuntimeException("实体不存在: " + entityId));
        
        entity.setEntityText(dto.getEntityText());
        entity.setEntityType(dto.getEntityType());
        entity.setStartPos(dto.getStartPos());
        entity.setEndPos(dto.getEndPos());
        entity.setIsNegated(dto.getIsNegated());
        entity.setIsUncertain(dto.getIsUncertain());
        entity.setSource("human");
        entity.setAnnotatedBy(userId);
        
        return entityRepository.save(entity);
    }

    @Transactional
    public Entity addEntity(Long documentId, EntityDTO dto, Long userId) {
        Entity entity = new Entity();
        entity.setDocumentId(documentId);
        entity.setEntityText(dto.getEntityText());
        entity.setEntityType(dto.getEntityType());
        entity.setStartPos(dto.getStartPos());
        entity.setEndPos(dto.getEndPos());
        entity.setIsNegated(dto.getIsNegated());
        entity.setIsUncertain(dto.getIsUncertain());
        entity.setConfidence(1.0f);
        entity.setSource("human");
        entity.setAnnotatedBy(userId);
        
        return entityRepository.save(entity);
    }

    @Transactional
    public void deleteEntity(Long entityId) {
        entityRepository.deleteById(entityId);
    }

    @Transactional
    public Relation updateRelation(Long relationId, RelationDTO dto, Long userId) {
        Relation relation = relationRepository.findById(relationId)
            .orElseThrow(() -> new RuntimeException("关系不存在: " + relationId));
        
        relation.setHeadEntityId(dto.getHeadEntityId());
        relation.setTailEntityId(dto.getTailEntityId());
        relation.setRelationType(dto.getRelationType());
        relation.setSource("human");
        relation.setAnnotatedBy(userId);
        
        return relationRepository.save(relation);
    }

    @Transactional
    public Relation addRelation(Long documentId, RelationDTO dto, Long userId) {
        Relation relation = new Relation();
        relation.setDocumentId(documentId);
        relation.setHeadEntityId(dto.getHeadEntityId());
        relation.setTailEntityId(dto.getTailEntityId());
        relation.setRelationType(dto.getRelationType());
        relation.setConfidence(1.0f);
        relation.setSource("human");
        relation.setAnnotatedBy(userId);
        
        return relationRepository.save(relation);
    }

    @Transactional
    public void deleteRelation(Long relationId) {
        relationRepository.deleteById(relationId);
    }

    @Transactional
    public Document markAsAnnotated(Long documentId, Long userId) {
        Document doc = getDocumentById(documentId);
        doc.setStatus("annotated");
        doc.setAnnotatedBy(userId);
        doc.setAnnotatedAt(java.time.LocalDateTime.now());
        return documentRepository.save(doc);
    }

    @Async
    @Transactional
    public void processBatchTask(Long taskId, List<DocumentRequestDTO> documents, Long userId) {
        batchTaskService.startTask(taskId, documents.size());
        
        int processed = 0;
        int failed = 0;
        
        for (DocumentRequestDTO docRequest : documents) {
            try {
                Document doc = createDocument(docRequest, userId);
                processDocument(doc.getId());
                processed++;
                batchTaskService.updateProgress(taskId, processed, failed);
            } catch (Exception e) {
                log.error("批量处理文档失败: {}", docRequest.getTitle(), e);
                failed++;
                batchTaskService.updateProgress(taskId, processed, failed);
            }
        }
        
        batchTaskService.completeTask(taskId, processed, failed);
    }

    private EntityDTO toEntityDTO(Entity entity) {
        EntityDTO dto = new EntityDTO();
        dto.setId(entity.getId());
        dto.setEntityText(entity.getEntityText());
        dto.setEntityType(entity.getEntityType());
        dto.setStartPos(entity.getStartPos());
        dto.setEndPos(entity.getEndPos());
        dto.setIsNegated(entity.getIsNegated());
        dto.setIsUncertain(entity.getIsUncertain());
        dto.setConfidence(entity.getConfidence());
        dto.setSource(entity.getSource());
        return dto;
    }

    private RelationDTO toRelationDTO(Relation relation, List<Entity> entities) {
        RelationDTO dto = new RelationDTO();
        dto.setId(relation.getId());
        dto.setHeadEntityId(relation.getHeadEntityId());
        dto.setTailEntityId(relation.getTailEntityId());
        dto.setRelationType(relation.getRelationType());
        dto.setConfidence(relation.getConfidence());
        dto.setSource(relation.getSource());
        
        entities.stream()
            .filter(e -> e.getId().equals(relation.getHeadEntityId()))
            .findFirst()
            .ifPresent(e -> dto.setHeadEntityText(e.getEntityText()));
        
        entities.stream()
            .filter(e -> e.getId().equals(relation.getTailEntityId()))
            .findFirst()
            .ifPresent(e -> dto.setTailEntityText(e.getEntityText()));
        
        return dto;
    }

    private TimelineDTO toTimelineDTO(Timeline timeline) {
        TimelineDTO dto = new TimelineDTO();
        dto.setId(timeline.getId());
        dto.setTimeExpression(timeline.getTimeExpression());
        dto.setNormalizedDate(timeline.getNormalizedDate());
        dto.setNormalizedDatetime(timeline.getNormalizedDatetime());
        dto.setAssociatedEvent(timeline.getAssociatedEvent());
        dto.setEntityId(timeline.getEntityId());
        dto.setConfidence(timeline.getConfidence());
        return dto;
    }

    public StatusCountDTO getStatusCounts() {
        List<Object[]> counts = documentRepository.countByStatus();
        StatusCountDTO dto = new StatusCountDTO();
        for (Object[] row : counts) {
            String status = (String) row[0];
            Long count = (Long) row[1];
            switch (status) {
                case "pending":
                    dto.setPending(count);
                    break;
                case "processing":
                    dto.setProcessing(count);
                    break;
                case "completed":
                    dto.setCompleted(count);
                    break;
                case "annotated":
                    dto.setAnnotated(count);
                    break;
                case "failed":
                    dto.setFailed(count);
                    break;
            }
        }
        return dto;
    }

    @Transactional
    public void batchUpdateStatus(List<Long> documentIds, String status, Long userId) {
        List<Document> docs = documentRepository.findAllById(documentIds);
        for (Document doc : docs) {
            doc.setStatus(status);
            if ("annotated".equals(status)) {
                doc.setAnnotatedBy(userId);
                doc.setAnnotatedAt(java.time.LocalDateTime.now());
            }
        }
        documentRepository.saveAll(docs);
    }

    public List<ConsistencyConflictDTO> checkConsistency() {
        List<Object[]> groups = entityRepository.findEntityTypeGroups();
        Map<String, Set<String>> entityTypeMap = new HashMap<>();
        
        for (Object[] row : groups) {
            String entityText = (String) row[0];
            String entityType = (String) row[1];
            if (entityText == null || entityText.isEmpty()) continue;
            
            entityTypeMap.computeIfAbsent(entityText, k -> new HashSet<>()).add(entityType);
        }

        Set<String> conflictEntityTexts = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : entityTypeMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflictEntityTexts.add(entry.getKey());
            }
        }

        if (conflictEntityTexts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Entity> allConflictEntities = new ArrayList<>();
        for (String text : conflictEntityTexts) {
            allConflictEntities.addAll(entityRepository.findByEntityTextIgnoreCase(text));
        }

        Set<Long> docIds = allConflictEntities.stream()
            .map(Entity::getDocumentId)
            .collect(Collectors.toSet());
        
        List<Document> docs = documentRepository.findAllById(docIds);
        Map<Long, Document> docMap = docs.stream()
            .collect(Collectors.toMap(Document::getId, d -> d));

        Map<String, List<EntityAnnotationDTO>> entityMap = new HashMap<>();
        for (Entity entity : allConflictEntities) {
            String key = entity.getEntityText().trim().toLowerCase();
            if (key.isEmpty()) continue;
            
            EntityAnnotationDTO ann = new EntityAnnotationDTO();
            ann.setDocumentId(entity.getDocumentId());
            Document doc = docMap.get(entity.getDocumentId());
            ann.setDocumentTitle(doc != null ? doc.getTitle() : "");
            ann.setEntityId(entity.getId());
            ann.setEntityType(entity.getEntityType());
            ann.setStartPos(entity.getStartPos());
            ann.setEndPos(entity.getEndPos());
            ann.setSource(entity.getSource());
            
            entityMap.computeIfAbsent(key, k -> new ArrayList<>()).add(ann);
        }

        List<ConsistencyConflictDTO> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<EntityAnnotationDTO>> entry : entityMap.entrySet()) {
            List<EntityAnnotationDTO> annotations = entry.getValue();
            if (annotations.isEmpty()) continue;
            
            ConsistencyConflictDTO conflict = new ConsistencyConflictDTO();
            conflict.setEntityText(annotations.get(0).getEntityText());
            conflict.setAnnotations(annotations);
            conflicts.add(conflict);
        }

        return conflicts;
    }

    @Transactional
    public void resolveConsistencyConflict(String entityText, String targetType, Long userId) {
        List<Entity> entities = entityRepository.findByEntityTextIgnoreCase(entityText);
        
        for (Entity entity : entities) {
            entity.setEntityType(targetType);
            entity.setSource("human");
            entity.setAnnotatedBy(userId);
        }
        entityRepository.saveAll(entities);
    }

    public List<ExportDocumentDTO> exportAnnotations(List<Long> documentIds) {
        List<Document> docs;
        if (documentIds == null || documentIds.isEmpty()) {
            docs = documentRepository.findAnnotatedOrCompleted();
        } else {
            docs = documentRepository.findAllById(documentIds);
        }

        List<ExportDocumentDTO> result = new ArrayList<>();
        for (Document doc : docs) {
            ExportDocumentDTO exportDoc = new ExportDocumentDTO();
            exportDoc.setText(doc.getContent());

            List<Entity> entities = entityRepository.findByDocumentId(doc.getId());
            
            Map<String, Entity> humanEntityMap = new HashMap<>();
            for (Entity e : entities) {
                if ("human".equals(e.getSource())) {
                    String key = e.getStartPos() + "-" + e.getEndPos();
                    humanEntityMap.put(key, e);
                }
            }

            List<Entity> filteredEntities = new ArrayList<>();
            Set<Long> addedEntityIds = new HashSet<>();
            
            for (Entity e : entities) {
                String key = e.getStartPos() + "-" + e.getEndPos();
                if ("human".equals(e.getSource())) {
                    if (!addedEntityIds.contains(e.getId())) {
                        filteredEntities.add(e);
                        addedEntityIds.add(e.getId());
                    }
                } else {
                    if (!humanEntityMap.containsKey(key) && !addedEntityIds.contains(e.getId())) {
                        filteredEntities.add(e);
                        addedEntityIds.add(e.getId());
                    }
                }
            }

            filteredEntities.sort(Comparator.comparingInt(Entity::getStartPos));

            Map<Long, Integer> entityIndexMap = new HashMap<>();
            List<ExportEntityDTO> exportEntities = new ArrayList<>();
            for (int i = 0; i < filteredEntities.size(); i++) {
                Entity e = filteredEntities.get(i);
                entityIndexMap.put(e.getId(), i);
                
                ExportEntityDTO ee = new ExportEntityDTO();
                ee.setText(e.getEntityText());
                ee.setType(e.getEntityType());
                ee.setStart(e.getStartPos());
                ee.setEnd(e.getEndPos());
                exportEntities.add(ee);
            }
            exportDoc.setEntities(exportEntities);

            List<Relation> relations = relationRepository.findByDocumentId(doc.getId());
            List<ExportRelationDTO> exportRelations = new ArrayList<>();
            int entityCount = exportEntities.size();
            
            for (Relation r : relations) {
                Integer headIdx = entityIndexMap.get(r.getHeadEntityId());
                Integer tailIdx = entityIndexMap.get(r.getTailEntityId());
                
                if (headIdx != null && tailIdx != null 
                    && headIdx >= 0 && headIdx < entityCount 
                    && tailIdx >= 0 && tailIdx < entityCount) {
                    ExportRelationDTO er = new ExportRelationDTO();
                    er.setHead(headIdx);
                    er.setTail(tailIdx);
                    er.setType(r.getRelationType());
                    exportRelations.add(er);
                }
            }
            exportDoc.setRelations(exportRelations);

            result.add(exportDoc);
        }

        return result;
    }
}
