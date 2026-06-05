package com.emr.ner.repository;

import com.emr.ner.entity.Entity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface EntityRepository extends JpaRepository<Entity, Long> {

    List<Entity> findByDocumentId(Long documentId);

    List<Entity> findByDocumentIdAndEntityType(Long documentId, String entityType);

    @Modifying
    @Transactional
    @Query("DELETE FROM Entity e WHERE e.documentId = :documentId AND e.source = 'model'")
    void deleteModelEntitiesByDocumentId(Long documentId);

    @Query("SELECT e FROM Entity e WHERE e.source = 'human' OR e.source = 'model'")
    List<Entity> findAllEntities();

    @Query("SELECT e FROM Entity e WHERE e.documentId IN :documentIds")
    List<Entity> findByDocumentIds(List<Long> documentIds);

    @Query("SELECT LOWER(TRIM(e.entityText)), e.entityType, COUNT(e) FROM Entity e " +
           "GROUP BY LOWER(TRIM(e.entityText)), e.entityType")
    List<Object[]> findEntityTypeGroups();

    @Query("SELECT e FROM Entity e WHERE LOWER(TRIM(e.entityText)) = LOWER(TRIM(:entityText))")
    List<Entity> findByEntityTextIgnoreCase(String entityText);
}
