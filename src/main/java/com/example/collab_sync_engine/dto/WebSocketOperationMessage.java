package com.example.collab_sync_engine.dto;


import com.example.collab_sync_engine.Enum.OperationType;

public class WebSocketOperationMessage {

    private String messageType;

    private String operationId;

    private String clientId;

    private long baseVersion;

    private OperationType type;

    private int position;

    private String text;

    private Integer length;

    // Phase 10: Presence fields
    private Integer cursorPosition;

    private Boolean typing;

    private Integer selectionStart;

    private Integer selectionEnd;

    public WebSocketOperationMessage() {
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
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

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Integer getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(Integer cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    public Boolean getTyping() {
        return typing;
    }

    public void setTyping(Boolean typing) {
        this.typing = typing;
    }

    public Integer getSelectionStart() {
        return selectionStart;
    }

    public void setSelectionStart(Integer selectionStart) {
        this.selectionStart = selectionStart;
    }

    public Integer getSelectionEnd() {
        return selectionEnd;
    }

    public void setSelectionEnd(Integer selectionEnd) {
        this.selectionEnd = selectionEnd;
    }
}