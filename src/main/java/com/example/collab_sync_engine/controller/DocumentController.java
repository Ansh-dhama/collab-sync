package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.dto.CreateDocumentRequest;
import com.example.collab_sync_engine.dto.DocumentResponse;
import com.example.collab_sync_engine.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(
            @Valid @RequestBody CreateDocumentRequest request
    ) {
        return documentService.createDocument(request);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse getDocumentById(
            @PathVariable String documentId
    ) {
        return documentService.getDocumentById(documentId);
    }

    @GetMapping
    public List<DocumentResponse> getAllDocuments() {
        return documentService.getAllDocuments();
    }
}