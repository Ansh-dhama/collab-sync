package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.dto.RevertDocumentRequest;
import com.example.collab_sync_engine.dto.RevertDocumentResponse;
import com.example.collab_sync_engine.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}")
public class DocumentRevertController {

    private final OperationService operationService;

    public DocumentRevertController(OperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping("/revert")
    public RevertDocumentResponse revertDocument(
            @PathVariable String documentId,
            @Valid @RequestBody RevertDocumentRequest request
    ) {
        return operationService.revertDocument(documentId, request);
    }
}