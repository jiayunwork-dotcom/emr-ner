package com.emr.ner.repository;

import com.emr.ner.entity.Timeline;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TimelineRepository extends JpaRepository<Timeline, Long> {

    List<Timeline> findByDocumentId(Long documentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Timeline t WHERE t.documentId = :documentId")
    void deleteByDocumentId(Long documentId);
}
