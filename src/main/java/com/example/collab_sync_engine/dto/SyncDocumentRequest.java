
package com.example.collab_sync_engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SyncDocumentRequest(

        @NotBlank(message = "Client id is required")
        String clientId,

        @Min(value = 0, message = "Last known server version cannot be negative")
        long lastKnownServerVersion,

        @NotEmpty(message = "Pending operations cannot be empty")
        List<@Valid SyncOperationRequest> pendingOperations

) {
}