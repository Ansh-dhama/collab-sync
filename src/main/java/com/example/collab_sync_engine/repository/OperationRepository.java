package com.example.collab_sync_engine.repository;

import com.example.collab_sync_engine.model.DocumentOperation;
import org.springframework.data.mongodb.repository.MongoRepository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

public interface OperationRepository extends MongoRepository<DocumentOperation, String> {

    boolean existsByDocumentIdAndClientIdAndOperationId(
            String documentId,
            String clientId,
            String operationId
    );

    Optional<DocumentOperation> findByDocumentIdAndClientIdAndOperationId(
            String documentId,
            String clientId,
            String operationId
    );

    List<DocumentOperation> findByDocumentIdAndServerVersionGreaterThanOrderByServerVersionAsc(
            String documentId,
            long serverVersion
    );
    List<DocumentOperation> findByDocumentIdOrderByServerVersionAsc(String documentId);
}