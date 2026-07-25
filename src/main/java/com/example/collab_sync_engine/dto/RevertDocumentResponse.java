package com.example.collab_sync_engine.dto;

import java.time.Instant;

public class RevertDocumentResponse {

    private String documentId;
    private String operationId;
    private String clientId;
    private long baseVersion;
    private long revertedToVersion;
    private long serverVersion;
    private String currentContent;
    private Instant createdAt;

    public RevertDocumentResponse() {
    }

    public RevertDocumentResponse(
            String documentId,
            String operationId,
            String clientId,
            long baseVersion,
            long revertedToVersion,
            long serverVersion,
            String currentContent,
            Instant createdAt
    ) {
        this.documentId = documentId;
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.revertedToVersion = revertedToVersion;
        this.serverVersion = serverVersion;
        this.currentContent = currentContent;
        this.createdAt = createdAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public long getRevertedToVersion() {
        return revertedToVersion;
    }

    public void setRevertedToVersion(long revertedToVersion) {
        this.revertedToVersion = revertedToVersion;
    }

    public long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}