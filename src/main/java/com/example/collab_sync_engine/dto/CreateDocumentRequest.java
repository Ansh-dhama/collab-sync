package com.example.collab_sync_engine.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDocumentRequest(

        @NotBlank(message = "Document title is required")
        String title,

        String initialContent,

        String createdBy
) {
}