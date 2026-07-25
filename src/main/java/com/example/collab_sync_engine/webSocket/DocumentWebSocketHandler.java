package com.example.collab_sync_engine.webSocket;

import com.example.collab_sync_engine.dto.*;
import com.example.collab_sync_engine.exception.DocumentNotFoundException;
import com.example.collab_sync_engine.model.CollaborativeDocument;
import com.example.collab_sync_engine.repository.DocumentRepository;
import com.example.collab_sync_engine.service.OperationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Set;

@Component
public class DocumentWebSocketHandler extends TextWebSocketHandler {

    private final OperationService operationService;
    private final DocumentRepository documentRepository;
    private final DocumentSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public DocumentWebSocketHandler(
            OperationService operationService,
            DocumentRepository documentRepository,
            DocumentSessionManager sessionManager,
            ObjectMapper objectMapper
    ) {
        this.operationService = operationService;
        this.documentRepository = documentRepository;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String documentId = extractDocumentId(session);
        String clientId = extractQueryParam(session, "clientId");
        long lastKnownVersion = extractLongQueryParam(session, "lastKnownVersion", 0L);

        if (clientId == null || clientId.isBlank()) {
            session.close(CloseStatus.BAD_DATA.withReason("clientId is required"));
            return;
        }

        CollaborativeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        sessionManager.addSession(
                documentId,
                clientId,
                session,
                lastKnownVersion
        );

        Set<String> activeClients = sessionManager.getActiveClients(documentId);

        WebSocketServerMessage connectedMessage =
                WebSocketServerMessage.connected(
                        documentId,
                        clientId,
                        lastKnownVersion,
                        activeClients
                );

        sessionManager.sendToSession(session, connectedMessage);

        WebSocketServerMessage syncMessage =
                WebSocketServerMessage.documentSync(document);

        sessionManager.sendToSession(session, syncMessage);

        WebSocketServerMessage joinedMessage =
                WebSocketServerMessage.clientJoined(
                        documentId,
                        clientId,
                        activeClients
                );

        sessionManager.broadcastToDocument(documentId, joinedMessage);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        SessionInfo sessionInfo =
                sessionManager.getSessionInfo(session.getId());

        if (sessionInfo == null) {
            sendError(session, null, null, "Session is not registered");
            return;
        }

        sessionManager.updateLastSeen(session.getId());

        String documentId = sessionInfo.documentId();
        String clientIdFromUrl = sessionInfo.clientId();

        try {
            WebSocketOperationMessage webSocketMessage =
                    objectMapper.readValue(
                            message.getPayload(),
                            WebSocketOperationMessage.class
                    );

            String messageType = webSocketMessage.getMessageType();

            if ("OPERATION".equalsIgnoreCase(messageType)) {
                handleOperationMessage(
                        session,
                        documentId,
                        clientIdFromUrl,
                        webSocketMessage
                );
                return;
            }

            if ("PRESENCE".equalsIgnoreCase(messageType)) {
                handlePresenceMessage(
                        session,
                        documentId,
                        clientIdFromUrl,
                        webSocketMessage
                );
                return;
            }

            sendError(
                    session,
                    documentId,
                    clientIdFromUrl,
                    "Unsupported messageType"
            );

        } catch (Exception ex) {
            sendError(
                    session,
                    documentId,
                    clientIdFromUrl,
                    ex.getMessage()
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) throws Exception {
        SessionInfo removedSessionInfo =
                sessionManager.removeSession(session.getId());

        if (removedSessionInfo == null) {
            return;
        }

        String documentId = removedSessionInfo.documentId();
        String clientId = removedSessionInfo.clientId();

        WebSocketServerMessage leftMessage =
                WebSocketServerMessage.clientLeft(
                        documentId,
                        clientId,
                        sessionManager.getActiveClients(documentId)
                );

        sessionManager.broadcastToDocument(documentId, leftMessage);
    }

    @Override
    public void handleTransportError(
            WebSocketSession session,
            Throwable exception
    ) throws Exception {
        if (session != null && session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void sendError(
            WebSocketSession session,
            String documentId,
            String clientId,
            String errorMessage
    ) throws Exception {
        WebSocketServerMessage error =
                WebSocketServerMessage.error(
                        documentId,
                        clientId,
                        errorMessage
                );

        sessionManager.sendToSession(session, error);
    }
    private void handleOperationMessage(
            WebSocketSession session,
            String documentId,
            String clientIdFromUrl,
            WebSocketOperationMessage webSocketMessage
    ) throws Exception {

        OperationRequest operationRequest = new OperationRequest();

        operationRequest.setOperationId(webSocketMessage.getOperationId());
        operationRequest.setClientId(clientIdFromUrl);
        operationRequest.setBaseVersion(webSocketMessage.getBaseVersion());
        operationRequest.setType(webSocketMessage.getType());
        operationRequest.setPosition(webSocketMessage.getPosition());
        operationRequest.setText(webSocketMessage.getText());
        operationRequest.setLength(webSocketMessage.getLength());

        OperationResponse operationResponse =
                operationService.applyOperation(documentId, operationRequest);

        WebSocketServerMessage operationAppliedMessage =
                WebSocketServerMessage.operationApplied(
                        operationResponse,
                        sessionManager.getActiveClients(documentId)
                );
        sessionManager.broadcastToDocument(documentId, operationAppliedMessage);
    }

    private String extractDocumentId(WebSocketSession session) {
        URI uri = session.getUri();

        if (uri == null) {
            throw new IllegalArgumentException("WebSocket URI is missing");
        }

        String path = uri.getPath();

        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("WebSocket path is missing");
        }

        String[] parts = path.split("/");

        return parts[parts.length - 1];
    }
    private void handlePresenceMessage(
            WebSocketSession session,
            String documentId,
            String clientIdFromUrl,
            WebSocketOperationMessage webSocketMessage
    ) throws Exception {

        validatePresenceMessage(webSocketMessage);

        WebSocketServerMessage presenceUpdateMessage =
                WebSocketServerMessage.presenceUpdate(
                        documentId,
                        clientIdFromUrl,
                        webSocketMessage.getCursorPosition(),
                        webSocketMessage.getTyping(),
                        webSocketMessage.getSelectionStart(),
                        webSocketMessage.getSelectionEnd(),
                        sessionManager.getActiveClients(documentId)
                );

        String payload =
                objectMapper.writeValueAsString(presenceUpdateMessage);

        sessionManager.broadcastToDocumentExcept(
                documentId,
                session.getId(),
                payload
        );
    }

    private String extractQueryParam(
            WebSocketSession session,
            String paramName
    ) {
        URI uri = session.getUri();

        if (uri == null || uri.getQuery() == null) {
            return null;
        }

        String[] queryParams = uri.getQuery().split("&");

        for (String queryParam : queryParams) {
            String[] keyValue = queryParam.split("=", 2);

            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }

        return null;
    }
    private void validatePresenceMessage(
            WebSocketOperationMessage webSocketMessage
    ) {
        if (webSocketMessage.getCursorPosition() != null &&
                webSocketMessage.getCursorPosition() < 0) {
            throw new IllegalArgumentException("Cursor position cannot be negative");
        }

        if (webSocketMessage.getSelectionStart() != null &&
                webSocketMessage.getSelectionStart() < 0) {
            throw new IllegalArgumentException("Selection start cannot be negative");
        }

        if (webSocketMessage.getSelectionEnd() != null &&
                webSocketMessage.getSelectionEnd() < 0) {
            throw new IllegalArgumentException("Selection end cannot be negative");
        }

        if (webSocketMessage.getSelectionStart() != null &&
                webSocketMessage.getSelectionEnd() != null &&
                webSocketMessage.getSelectionStart() > webSocketMessage.getSelectionEnd()) {
            throw new IllegalArgumentException("Selection start cannot be greater than selection end");
        }
    }
    private long extractLongQueryParam(
            WebSocketSession session,
            String paramName,
            long defaultValue
    ) {
        String value = extractQueryParam(session, paramName);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}