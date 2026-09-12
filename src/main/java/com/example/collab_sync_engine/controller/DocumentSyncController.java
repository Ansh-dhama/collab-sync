package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.Enum.SyncOperationStatus;
import com.example.collab_sync_engine.dto.SyncDocumentRequest;
import com.example.collab_sync_engine.dto.SyncDocumentResponse;
import com.example.collab_sync_engine.dto.WebSocketServerMessage;
import com.example.collab_sync_engine.repository.DocumentRepository;
import com.example.collab_sync_engine.service.OperationService;
import com.example.collab_sync_engine.webSocket.DocumentSessionManager;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}")
public class DocumentSyncController {

    private final OperationService operationService;
    private final DocumentRepository documentRepository;
    private final DocumentSessionManager sessionManager;

    public DocumentSyncController(
            OperationService operationService,
            DocumentRepository documentRepository,
            DocumentSessionManager sessionManager
    ) {
        this.operationService = operationService;
        this.documentRepository = documentRepository;
        this.sessionManager = sessionManager;
    }

    @PostMapping("/sync")
    public SyncDocumentResponse syncPendingOperations(
            @PathVariable String documentId,
            @Valid @RequestBody SyncDocumentRequest request
    ) {
        SyncDocumentResponse response = operationService.syncPendingOperations(documentId, request);

        boolean hasAppliedOperation = response.results() != null &&
                response.results()
                        .stream()
                        .anyMatch(result -> result.status() == SyncOperationStatus.APPLIED);

        /*
         * /sync is called through HTTP after reconnect.
         * Without this broadcast, the offline user receives merged content,
         * but users who stayed online do not see the offline merge until refresh.
         */
        if (hasAppliedOperation) {
            documentRepository.findById(documentId).ifPresent(document -> {
                try {
                    sessionManager.broadcastToDocument(
                            documentId,
                            WebSocketServerMessage.documentSync(document)
                    );
                } catch (Exception ignored) {
                    // HTTP sync already succeeded. A WebSocket broadcast failure should not fail the API response.
                }
            });
        }

        return response;
    }
}
