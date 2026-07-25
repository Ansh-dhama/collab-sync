package com.example.collab_sync_engine.dto;

import java.time.Instant;

public record DocumentResponse(
        String documentId,
        String title,
        String currentContent,
        long currentVersion,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public long getCurrentVersion() {
        return currentVersion;
    }

    public String getCurrentContent() {
        return currentContent;
    }
}