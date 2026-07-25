package com.example.collab_sync_engine.dto;

import java.time.Instant;

public record SessionInfo(
        String sessionId,
        String documentId,
        String clientId,
        long lastKnownVersion,
        Instant connectedAt,
        Instant lastSeenAt
) {
}