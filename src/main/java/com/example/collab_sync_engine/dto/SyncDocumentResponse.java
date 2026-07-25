package com.example.collab_sync_engine.dto;

import java.util.List;

public record SyncDocumentResponse(

        String documentId,

        String clientId,

        long latestServerVersion,

        String currentContent,

        List<SyncOperationResult> results

) {
}