package com.emr.ner.repository;

import com.emr.ner.entity.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface RelationRepository extends JpaRepository<Relation, Long> {

    List<Relation> findByDocumentId(Long documentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Relation r WHERE r.documentId = :documentId AND r.source = 'model'")
    void deleteModelRelationsByDocumentId(Long documentId);
}
