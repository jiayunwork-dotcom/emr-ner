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

import java.util.ArrayList;
import java.util.List;
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
                dto.setId(entity.getId());
            }

            for (RelationDTO dto : inferenceResult.getRelations()) {
                Relation relation = new Relation();
                relation.setDocumentId(documentId);
                relation.setHeadEntityId(dto.getHeadEntityId());
                relation.setTailEntityId(dto.getTailEntityId());
                relation.setRelationType(dto.getRelationType());
                relation.setConfidence(dto.getConfidence());
                relation.setSource("model");
                relation = relationRepository.save(relation);
                dto.setId(relation.getId());
            }

            for (TimelineDTO dto : inferenceResult.getTimelines()) {
                Timeline timeline = new Timeline();
                timeline.setDocumentId(documentId);
                timeline.setTimeExpression(dto.getTimeExpression());
                timeline.setNormalizedDate(dto.getNormalizedDate());
                timeline.setNormalizedDatetime(dto.getNormalizedDatetime());
                timeline.setAssociatedEvent(dto.getAssociatedEvent());
                timeline.setEntityId(dto.getEntityId());
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
}
