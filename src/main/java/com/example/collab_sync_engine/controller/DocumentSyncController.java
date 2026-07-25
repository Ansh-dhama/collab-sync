package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.dto.SyncDocumentRequest;
import com.example.collab_sync_engine.dto.SyncDocumentResponse;
import com.example.collab_sync_engine.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}")
public class DocumentSyncController {

    private final OperationService operationService;

    public DocumentSyncController(OperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping("/sync")
    public SyncDocumentResponse syncPendingOperations(
            @PathVariable String documentId,
            @Valid @RequestBody SyncDocumentRequest request
    ) {
        return operationService.syncPendingOperations(documentId, request);
    }
}