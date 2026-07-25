package com.example.collab_sync_engine.engine;

import com.example.collab_sync_engine.Enum.OperationType;
import com.example.collab_sync_engine.dto.OperationRequest;
import com.example.collab_sync_engine.model.DocumentOperation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationalTransformationEngineTest {

    private final OperationalTransformationEngine transformationEngine =
            new OperationalTransformationEngine();

    @Test
    void shouldShiftIncomingInsertRightWhenAppliedInsertIsBeforeOrSamePosition() {
        OperationRequest incoming = operationRequest(
                "op-B",
                "user-B",
                0,
                OperationType.INSERT,
                6,
                "Beautiful ",
                null
        );

        DocumentOperation applied = documentOperation(
                "op-A",
                "doc-1",
                "user-A",
                0,
                1,
                OperationType.INSERT,
                6,
                "Big ",
                null
        );

        OperationRequest transformed =
                transformationEngine.transformAgainstHistory(
                        incoming,
                        List.of(applied)
                );

        assertEquals(OperationType.INSERT, transformed.getType());
        assertEquals(10, transformed.getPosition());
        assertEquals("Beautiful ", transformed.getText());
    }

    private OperationRequest operationRequest(
            String operationId,
            String clientId,
            long baseVersion,
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        OperationRequest request = new OperationRequest();

        request.setOperationId(operationId);
        request.setClientId(clientId);
        request.setBaseVersion(baseVersion);
        request.setType(type);
        request.setPosition(position);
        request.setText(text);
        request.setLength(length);

        return request;
    }

    private DocumentOperation documentOperation(
            String operationId,
            String documentId,
            String clientId,
            long baseVersion,
            long serverVersion,
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        DocumentOperation operation = new DocumentOperation();

        operation.setOperationId(operationId);
        operation.setDocumentId(documentId);
        operation.setClientId(clientId);
        operation.setBaseVersion(baseVersion);
        operation.setServerVersion(serverVersion);
        operation.setType(type);
        operation.setPosition(position);
        operation.setText(text);
        operation.setLength(length);
        operation.setCreatedAt(Instant.now());

        return operation;
    }
}