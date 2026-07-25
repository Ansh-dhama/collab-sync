package com.example.collab_sync_engine.dto;

import com.example.collab_sync_engine.Enum.OperationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class OperationRequest {

    @NotBlank(message = "Operation id is required")
    private String operationId;

    @NotBlank(message = "Client id is required")
    private String clientId;

    @Min(value = 0, message = "Base version cannot be negative")
    private long baseVersion;

    @NotNull(message = "Operation type is required")
    private OperationType type;

    @Min(value = 0, message = "Position cannot be negative")
    private int position;

    private String text;

    private Integer length;

    public OperationRequest() {
    }

    public OperationRequest(
            String operationId,
            String clientId,
            long baseVersion,
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.type = type;
        this.position = position;
        this.text = text;
        this.length = length;
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

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setBaseVersion(long baseVersion) {
        this.baseVersion = baseVersion;
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
}