package com.example.collab_sync_engine.service;

import com.example.collab_sync_engine.dto.CreateDocumentRequest;
import com.example.collab_sync_engine.dto.DocumentResponse;
import com.example.collab_sync_engine.exception.DocumentNotFoundException;
import com.example.collab_sync_engine.model.CollaborativeDocument;
import com.example.collab_sync_engine.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public DocumentResponse createDocument(CreateDocumentRequest request) {
        Instant now = Instant.now();

        String initialContent = request.initialContent() == null
                ? ""
                : request.initialContent();

        CollaborativeDocument document = new CollaborativeDocument(
                request.title(),
                initialContent,
                initialContent,
                0L,
                request.createdBy(),
                now,
                now
        );

        CollaborativeDocument savedDocument = documentRepository.save(document);

        return toResponse(savedDocument);
    }


    public DocumentResponse getDocumentById(String documentId) {
        CollaborativeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        return toResponse(document);
    }


    public List<DocumentResponse> getAllDocuments() {
        return documentRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private DocumentResponse toResponse(CollaborativeDocument document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getCurrentContent(),
                document.getCurrentVersion(),
                document.getCreatedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}