package com.emr.ner.repository;

import com.emr.ner.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByStatus(String status, Pageable pageable);

    Page<Document> findByPatientId(String patientId, Pageable pageable);

    Page<Document> findByDocumentType(String documentType, Pageable pageable);

    @Query("SELECT d FROM Document d WHERE d.createdBy = :userId")
    Page<Document> findByCreatedBy(Long userId, Pageable pageable);

    List<Document> findByStatusOrderByIdAsc(String status);
}
