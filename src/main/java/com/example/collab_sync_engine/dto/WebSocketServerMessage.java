package com.example.collab_sync_engine.dto;

import com.example.collab_sync_engine.Enum.OperationType;
import com.example.collab_sync_engine.model.CollaborativeDocument;


import java.time.Instant;
import java.util.Set;

public class WebSocketServerMessage {

    private String messageType;

    private String documentId;

    private String operationId;

    private String clientId;

    private Long baseVersion;

    private Long serverVersion;

    private Long currentVersion;

    private OperationType type;

    private Integer position;

    private String text;

    private Integer length;

    private String currentContent;

    private Instant createdAt;

    private String message;

    private Set<String> activeClients;

    private Long lastKnownVersion;
    private Integer cursorPosition;
    private Boolean typing;
    private Integer selectionStart;
    private Integer selectionEnd;

    public WebSocketServerMessage() {
    }

    public static WebSocketServerMessage connected(
            String documentId,
            String clientId,
            long lastKnownVersion,
            Set<String> activeClients
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("CONNECTED");
        response.setDocumentId(documentId);
        response.setClientId(clientId);
        response.setLastKnownVersion(lastKnownVersion);
        response.setActiveClients(activeClients);
        response.setMessage("Connected to document WebSocket");

        return response;
    }

    public static WebSocketServerMessage documentSync(
            CollaborativeDocument document
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("DOCUMENT_SYNC");
        response.setDocumentId(document.getId());
        response.setCurrentVersion(document.getCurrentVersion());
        response.setServerVersion(document.getCurrentVersion());
        response.setCurrentContent(document.getCurrentContent());
        response.setMessage("Latest document state synced after reconnect");

        return response;
    }

    public static WebSocketServerMessage clientJoined(
            String documentId,
            String clientId,
            Set<String> activeClients
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("CLIENT_JOINED");
        response.setDocumentId(documentId);
        response.setClientId(clientId);
        response.setActiveClients(activeClients);
        response.setMessage(clientId + " joined the document");

        return response;
    }

    public static WebSocketServerMessage presenceUpdate(
            String documentId,
            String clientId,
            Integer cursorPosition,
            Boolean typing,
            Integer selectionStart,
            Integer selectionEnd,
            Set<String> activeClients
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("PRESENCE_UPDATE");
        response.setDocumentId(documentId);
        response.setClientId(clientId);
        response.setCursorPosition(cursorPosition);
        response.setTyping(typing);
        response.setSelectionStart(selectionStart);
        response.setSelectionEnd(selectionEnd);
        response.setActiveClients(activeClients);
        response.setMessage(clientId + " presence updated");

        return response;
    }

    public static WebSocketServerMessage clientLeft(
            String documentId,
            String clientId,
            Set<String> activeClients
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("CLIENT_LEFT");
        response.setDocumentId(documentId);
        response.setClientId(clientId);
        response.setActiveClients(activeClients);
        response.setMessage(clientId + " left the document");

        return response;
    }

    public static WebSocketServerMessage operationApplied(
            OperationResponse operationResponse,
            Set<String> activeClients
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("OPERATION_APPLIED");
        response.setDocumentId(operationResponse.getDocumentId());
        response.setOperationId(operationResponse.getOperationId());
        response.setClientId(operationResponse.getClientId());
        response.setBaseVersion(operationResponse.getBaseVersion());
        response.setServerVersion(operationResponse.getServerVersion());
        response.setCurrentVersion(operationResponse.getServerVersion());
        response.setType(operationResponse.getType());
        response.setPosition(operationResponse.getPosition());
        response.setText(operationResponse.getText());
        response.setLength(operationResponse.getLength());
        response.setCurrentContent(operationResponse.getCurrentContent());
        response.setCreatedAt(operationResponse.getCreatedAt());
        response.setActiveClients(activeClients);
        response.setMessage("Operation applied successfully");

        return response;
    }

    public static WebSocketServerMessage error(
            String documentId,
            String clientId,
            String message
    ) {
        WebSocketServerMessage response = new WebSocketServerMessage();

        response.setMessageType("ERROR");
        response.setDocumentId(documentId);
        response.setClientId(clientId);
        response.setMessage(message);

        return response;
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

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
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

    public Long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(Long serverVersion) {
        this.serverVersion = serverVersion;
    }

    public Long getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Long currentVersion) {
        this.currentVersion = currentVersion;
    }

    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Set<String> getActiveClients() {
        return activeClients;
    }

    public void setActiveClients(Set<String> activeClients) {
        this.activeClients = activeClients;
    }

    public Long getLastKnownVersion() {
        return lastKnownVersion;
    }

    public void setLastKnownVersion(Long lastKnownVersion) {
        this.lastKnownVersion = lastKnownVersion;
    }
}