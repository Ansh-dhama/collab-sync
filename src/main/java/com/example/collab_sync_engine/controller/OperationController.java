package com.example.collab_sync_engine.controller;

import com.example.collab_sync_engine.dto.OperationRequest;
import com.example.collab_sync_engine.dto.OperationResponse;
import com.example.collab_sync_engine.service.OperationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}/operations")
public class OperationController {

    private final OperationService operationService;

    public OperationController(OperationService operationService) {
        this.operationService = operationService;
    }

    @PostMapping
    public OperationResponse applyOperation(
            @PathVariable String documentId,
            @Valid @RequestBody OperationRequest request
    ) {
        return operationService.applyOperation(documentId, request);
    }

}