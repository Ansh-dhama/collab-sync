package com.example.collab_sync_engine.dto;

import com.example.collab_sync_engine.Enum.OperationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SyncOperationRequest(

        @NotBlank(message = "Operation id is required")
        String operationId,

        @Min(value = 0, message = "Base version cannot be negative")
        long baseVersion,

        @NotNull(message = "Operation type is required")
        OperationType type,

        @Min(value = 0, message = "Position cannot be negative")
        int position,

        String text,

        Integer length

) {
}