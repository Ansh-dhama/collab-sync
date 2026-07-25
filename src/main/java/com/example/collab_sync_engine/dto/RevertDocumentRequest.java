package com.example.collab_sync_engine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class RevertDocumentRequest {

    @NotBlank(message = "Operation id is required")
    private String operationId;

    @NotBlank(message = "Client id is required")
    private String clientId;

    @Min(value = 0, message = "Base version cannot be negative")
    private long baseVersion;

    @Min(value = 0, message = "Target version cannot be negative")
    private long targetVersion;

    public RevertDocumentRequest() {
    }

    public RevertDocumentRequest(
            String operationId,
            String clientId,
            long baseVersion,
            long targetVersion
    ) {
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.targetVersion = targetVersion;
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

    public long getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(long targetVersion) {
        this.targetVersion = targetVersion;
    }
}