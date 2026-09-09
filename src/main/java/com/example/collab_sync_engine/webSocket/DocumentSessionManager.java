package com.example.collab_sync_engine.webSocket;

import com.example.collab_sync_engine.dto.SessionInfo;
import com.example.collab_sync_engine.dto.WebSocketServerMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentSessionManager {

    private final Map<String, Map<String, WebSocketSession>> sessionsByDocument =
            new ConcurrentHashMap<>();

    private final Map<String, SessionInfo> sessionInfoBySessionId =
            new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public DocumentSessionManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void addSession(
            String documentId,
            String clientId,
            WebSocketSession session,
            long lastKnownVersion
    ) {
        Map<String, WebSocketSession> clientSessions =
                sessionsByDocument.computeIfAbsent(
                        documentId,
                        key -> new ConcurrentHashMap<>()
                );

        WebSocketSession oldSession = clientSessions.put(clientId, session);

        // Same documentId + same clientId means reconnect.
        // Close the old socket so one logical user has one active connection.
        if (oldSession != null && !oldSession.getId().equals(session.getId())) {
            sessionInfoBySessionId.remove(oldSession.getId());
            closeQuietly(
                    oldSession,
                    CloseStatus.NORMAL.withReason("Replaced by a newer session")
            );
        }

        Instant now = Instant.now();

        SessionInfo sessionInfo = new SessionInfo(
                session.getId(),
                documentId,
                clientId,
                lastKnownVersion,
                now,
                now
        );

        sessionInfoBySessionId.put(session.getId(), sessionInfo);
    }

    public SessionInfo removeSession(String sessionId) {
        SessionInfo sessionInfo = sessionInfoBySessionId.remove(sessionId);

        if (sessionInfo == null) {
            return null;
        }

        Map<String, WebSocketSession> documentSessions =
                sessionsByDocument.get(sessionInfo.documentId());

        if (documentSessions != null) {
            WebSocketSession existingSession =
                    documentSessions.get(sessionInfo.clientId());

            if (existingSession != null &&
                    existingSession.getId().equals(sessionId)) {
                documentSessions.remove(sessionInfo.clientId());
            }

            if (documentSessions.isEmpty()) {
                sessionsByDocument.remove(sessionInfo.documentId());
            }
        }

        return sessionInfo;
    }

    public SessionInfo getSessionInfo(String sessionId) {
        return sessionInfoBySessionId.get(sessionId);
    }

    public void updateLastSeen(String sessionId) {
        SessionInfo oldInfo = sessionInfoBySessionId.get(sessionId);

        if (oldInfo == null) {
            return;
        }

        SessionInfo updatedInfo = new SessionInfo(
                oldInfo.sessionId(),
                oldInfo.documentId(),
                oldInfo.clientId(),
                oldInfo.lastKnownVersion(),
                oldInfo.connectedAt(),
                Instant.now()
        );

        sessionInfoBySessionId.put(sessionId, updatedInfo);
    }

    public void updateLastKnownVersion(String sessionId, long lastKnownVersion) {
        SessionInfo oldInfo = sessionInfoBySessionId.get(sessionId);

        if (oldInfo == null) {
            return;
        }

        SessionInfo updatedInfo = new SessionInfo(
                oldInfo.sessionId(),
                oldInfo.documentId(),
                oldInfo.clientId(),
                lastKnownVersion,
                oldInfo.connectedAt(),
                Instant.now()
        );

        sessionInfoBySessionId.put(sessionId, updatedInfo);
    }

    public Set<String> getActiveClients(String documentId) {
        cleanupClosedSessions(documentId);

        Map<String, WebSocketSession> documentSessions =
                sessionsByDocument.get(documentId);

        if (documentSessions == null || documentSessions.isEmpty()) {
            return Set.of();
        }

        return new LinkedHashSet<>(documentSessions.keySet());
    }

    public void broadcastToDocument(
            String documentId,
            WebSocketServerMessage message
    ) throws IOException {
        String payload = objectMapper.writeValueAsString(message);
        broadcastPayloadToDocument(documentId, null, payload);
    }

    public void broadcastToDocumentExcept(
            String documentId,
            String excludedSessionId,
            String payload
    ) {
        broadcastPayloadToDocument(documentId, excludedSessionId, payload);
    }

    private void broadcastPayloadToDocument(
            String documentId,
            String excludedSessionId,
            String payload
    ) {
        Map<String, WebSocketSession> documentSessions =
                sessionsByDocument.get(documentId);

        if (documentSessions == null || documentSessions.isEmpty()) {
            return;
        }

        Set<String> closedSessionIds = new HashSet<>();

        documentSessions.forEach((clientId, session) -> {
            if (session == null || !session.isOpen()) {
                if (session != null) {
                    closedSessionIds.add(session.getId());
                }
                return;
            }

            if (excludedSessionId != null &&
                    session.getId().equals(excludedSessionId)) {
                return;
            }

            boolean sent = sendMessage(session, payload);

            if (!sent) {
                closedSessionIds.add(session.getId());
            }
        });

        for (String closedSessionId : closedSessionIds) {
            removeSession(closedSessionId);
        }
    }

    public void sendToSession(
            WebSocketSession session,
            WebSocketServerMessage message
    ) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }

        String payload = objectMapper.writeValueAsString(message);

        boolean sent = sendMessage(session, payload);

        if (!sent) {
            throw new IOException("Could not send WebSocket message");
        }
    }

    private boolean sendMessage(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) {
            return false;
        }

        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                    return true;
                }
            }
        } catch (IOException exception) {
            closeQuietly(
                    session,
                    CloseStatus.SERVER_ERROR.withReason("Message send failed")
            );
            return false;
        }

        return false;
    }

    private void cleanupClosedSessions(String documentId) {
        Map<String, WebSocketSession> documentSessions =
                sessionsByDocument.get(documentId);

        if (documentSessions == null || documentSessions.isEmpty()) {
            return;
        }

        Set<String> closedSessionIds = new HashSet<>();

        documentSessions.forEach((clientId, session) -> {
            if (session == null || !session.isOpen()) {
                if (session != null) {
                    closedSessionIds.add(session.getId());
                }
            }
        });

        for (String closedSessionId : closedSessionIds) {
            removeSession(closedSessionId);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus closeStatus) {
        try {
            if (session != null && session.isOpen()) {
                session.close(closeStatus);
            }
        } catch (Exception ignored) {
            // Ignore close errors. Session cleanup already happened.
        }
    }
}
