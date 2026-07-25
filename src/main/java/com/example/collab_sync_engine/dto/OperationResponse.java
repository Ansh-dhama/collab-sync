package com.example.collab_sync_engine.dto;


import com.example.collab_sync_engine.Enum.OperationType;

import java.time.Instant;

public class OperationResponse {

    private String documentId;

    private String operationId;

    private String clientId;

    private long baseVersion;

    private long serverVersion;

    private OperationType type;

    private int position;

    private String text;

    private Integer length;

    private String currentContent;

    private Instant createdAt;

    public OperationResponse() {
    }

    public OperationResponse(
            String documentId,
            String operationId,
            String clientId,
            long baseVersion,
            long serverVersion,
            OperationType type,
            int position,
            String text,
            Integer length,
            String currentContent,
            Instant createdAt
    ) {
        this.documentId = documentId;
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.serverVersion = serverVersion;
        this.type = type;
        this.position = position;
        this.text = text;
        this.length = length;
        this.currentContent = currentContent;
        this.createdAt = createdAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getClientId() {
        return clientId;
    }

    public long getBaseVersion() {
        return baseVersion;
    }

    public long getServerVersion() {
        return serverVersion;
    }

    public OperationType getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }

    public Integer getLength() {
        return length;
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setBaseVersion(long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}