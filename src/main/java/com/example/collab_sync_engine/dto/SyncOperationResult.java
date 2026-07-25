package com.example.collab_sync_engine.dto;

import com.example.collab_sync_engine.Enum.SyncOperationStatus;

public record SyncOperationResult(

        String operationId,

        SyncOperationStatus status,

        Long serverVersion,

        String message

) {
}