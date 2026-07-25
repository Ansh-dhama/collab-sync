package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.dto.DocumentVersionResponse;
import com.example.collab_sync_engine.dto.OperationHistoryResponse;
import com.example.collab_sync_engine.service.OperationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}")
public class DocumentHistoryController {

    private final OperationService operationService;

    public DocumentHistoryController(OperationService operationService) {
        this.operationService = operationService;
    }

    @GetMapping("/history")
    public List<OperationHistoryResponse> getDocumentHistory(
            @PathVariable String documentId
    ) {
        return operationService.getDocumentHistory(documentId);
    }

    @GetMapping("/versions/{version}")
    public DocumentVersionResponse getDocumentAtVersion(
            @PathVariable String documentId,
            @PathVariable long version
    ) {
        return operationService.getDocumentAtVersion(documentId, version);
    }
}