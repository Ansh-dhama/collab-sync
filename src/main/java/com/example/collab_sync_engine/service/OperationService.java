package com.example.collab_sync_engine.service;

import com.example.collab_sync_engine.Enum.OperationType;
import com.example.collab_sync_engine.Enum.SyncOperationStatus;
import com.example.collab_sync_engine.dto.*;
import com.example.collab_sync_engine.engine.DocumentLockManager;
import com.example.collab_sync_engine.engine.OperationalTransformationEngine;
import com.example.collab_sync_engine.exception.DocumentNotFoundException;
import com.example.collab_sync_engine.model.CollaborativeDocument;
import com.example.collab_sync_engine.model.DocumentOperation;
import com.example.collab_sync_engine.repository.DocumentRepository;
import com.example.collab_sync_engine.repository.OperationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OperationService {

    private final DocumentRepository documentRepository;
    private final OperationRepository operationRepository;
    private final DocumentStateService documentStateService;
    private final OperationalTransformationEngine transformationEngine;
    private final DocumentLockManager documentLockManager;

    public OperationService(
            DocumentRepository documentRepository,
            OperationRepository operationRepository,
            DocumentStateService documentStateService,
            OperationalTransformationEngine transformationEngine,
            DocumentLockManager documentLockManager
    ) {
        this.documentRepository = documentRepository;
        this.operationRepository = operationRepository;
        this.documentStateService = documentStateService;
        this.transformationEngine = transformationEngine;
        this.documentLockManager = documentLockManager;
    }

    // =========================================================
    // Normal live REST/WebSocket operation apply
    // =========================================================

    public OperationResponse applyOperation(String documentId, OperationRequest request) {
        Object lock = documentLockManager.getLock(documentId);

        synchronized (lock) {
            return applyOperationInternal(documentId, request);
        }
    }

    private OperationResponse applyOperationInternal(
            String documentId,
            OperationRequest request
    ) {
        validateCommonRequest(request);

        CollaborativeDocument document = getDocumentOrThrow(documentId);

        Optional<DocumentOperation> existingOperation =
                operationRepository.findByDocumentIdAndClientIdAndOperationId(
                        documentId,
                        request.getClientId(),
                        request.getOperationId()
                );

        if (existingOperation.isPresent()) {
            return toResponse(existingOperation.get(), document.getCurrentContent());
        }

        if (request.getBaseVersion() > document.getCurrentVersion()) {
            throw new IllegalArgumentException(
                    "Client baseVersion cannot be greater than server currentVersion. " +
                            "Client baseVersion: " + request.getBaseVersion() +
                            ", server currentVersion: " + document.getCurrentVersion()
            );
        }

        OperationRequest operationToApply = request;

        if (request.getBaseVersion() < document.getCurrentVersion()) {
            List<DocumentOperation> operationsAfterBaseVersion =
                    operationRepository.findByDocumentIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                            documentId,
                            request.getBaseVersion()
                    );

            if (containsRevertOperation(operationsAfterBaseVersion)) {
                throw new IllegalArgumentException(
                        "Cannot apply old operation because document was reverted after client baseVersion. " +
                                "Please refresh document and try again."
                );
            }

            operationToApply = transformationEngine.transformAgainstHistory(
                    request,
                    operationsAfterBaseVersion
            );
        }

        operationToApply = normalizeOperationForCurrentContent(
                operationToApply,
                document.getCurrentContent()
        );

        return persistOperationAndUpdateDocument(document, operationToApply, request.getBaseVersion());
    }

    // =========================================================
    // Offline reconnect sync
    // =========================================================
    // A batch of offline operations is different from normal live operations.
    // A2's position already includes A1 locally, so A2 must not be transformed
    // against A1 again. It should only be transformed against concurrent server
    // operations that this offline client did not know about.

    public SyncDocumentResponse syncPendingOperations(
            String documentId,
            SyncDocumentRequest request
    ) {
        validateSyncRequest(request);

        Object lock = documentLockManager.getLock(documentId);

        synchronized (lock) {
            getDocumentOrThrow(documentId);

            long batchBaseVersion = resolveBatchBaseVersion(request);
            Set<String> batchOperationIds = collectBatchOperationIds(request);

            List<SyncOperationResult> results = new ArrayList<>();
            boolean stopProcessing = false;

            for (SyncOperationRequest pendingOperation : request.pendingOperations()) {

                if (stopProcessing) {
                    results.add(new SyncOperationResult(
                            pendingOperation == null ? null : pendingOperation.operationId(),
                            SyncOperationStatus.SKIPPED,
                            null,
                            "Skipped because previous operation failed"
                    ));
                    continue;
                }

                try {
                    validateSyncOperation(pendingOperation);

                    CollaborativeDocument currentDocument = getDocumentOrThrow(documentId);

                    if (batchBaseVersion > currentDocument.getCurrentVersion()) {
                        throw new IllegalArgumentException(
                                "Client offline baseVersion cannot be greater than server currentVersion. " +
                                        "Client offline baseVersion: " + batchBaseVersion +
                                        ", server currentVersion: " + currentDocument.getCurrentVersion()
                        );
                    }

                    Optional<DocumentOperation> existingOperation =
                            operationRepository.findByDocumentIdAndClientIdAndOperationId(
                                    documentId,
                                    request.clientId(),
                                    pendingOperation.operationId()
                            );

                    if (existingOperation.isPresent()) {
                        results.add(new SyncOperationResult(
                                pendingOperation.operationId(),
                                SyncOperationStatus.DUPLICATE,
                                existingOperation.get().getServerVersion(),
                                "Operation already applied earlier"
                        ));
                        continue;
                    }

                    OperationResponse operationResponse =
                            applyOfflineBatchOperationInternal(
                                    currentDocument,
                                    request.clientId(),
                                    pendingOperation,
                                    batchBaseVersion,
                                    batchOperationIds
                            );

                    results.add(new SyncOperationResult(
                            pendingOperation.operationId(),
                            SyncOperationStatus.APPLIED,
                            operationResponse.getServerVersion(),
                            "Operation applied successfully"
                    ));

                } catch (Exception ex) {
                    results.add(new SyncOperationResult(
                            pendingOperation == null ? null : pendingOperation.operationId(),
                            SyncOperationStatus.FAILED,
                            null,
                            ex.getMessage()
                    ));

                    stopProcessing = true;
                }
            }

            CollaborativeDocument latestDocument = getDocumentOrThrow(documentId);

            return new SyncDocumentResponse(
                    documentId,
                    request.clientId(),
                    latestDocument.getCurrentVersion(),
                    safeContent(latestDocument.getCurrentContent()),
                    results
            );
        }
    }

    private OperationResponse applyOfflineBatchOperationInternal(
            CollaborativeDocument document,
            String clientId,
            SyncOperationRequest pendingOperation,
            long batchBaseVersion,
            Set<String> batchOperationIds
    ) {
        OperationRequest operationRequest = new OperationRequest(
                pendingOperation.operationId(),
                clientId,
                pendingOperation.baseVersion(),
                pendingOperation.type(),
                pendingOperation.position(),
                pendingOperation.text(),
                pendingOperation.length()
        );

        OperationRequest operationToApply = operationRequest;

        if (batchBaseVersion < document.getCurrentVersion()) {
            List<DocumentOperation> concurrentOperations =
                    operationRepository.findByDocumentIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                                    document.getId(),
                                    batchBaseVersion
                            )
                            .stream()
                            .filter(operation -> !batchOperationIds.contains(operation.getOperationId()))
                            .toList();

            if (containsRevertOperation(concurrentOperations)) {
                throw new IllegalArgumentException(
                        "Cannot sync offline operation because document was reverted while client was offline. " +
                                "Please refresh document and apply changes again."
                );
            }

            operationToApply = transformationEngine.transformAgainstHistory(
                    operationRequest,
                    concurrentOperations
            );
        }

        operationToApply = normalizeOperationForCurrentContent(
                operationToApply,
                document.getCurrentContent()
        );

        return persistOperationAndUpdateDocument(
                document,
                operationToApply,
                pendingOperation.baseVersion()
        );
    }

    private long resolveBatchBaseVersion(SyncDocumentRequest request) {
        return request.pendingOperations()
                .stream()
                .filter(operation -> operation != null)
                .mapToLong(SyncOperationRequest::baseVersion)
                .min()
                .orElse(request.lastKnownServerVersion());
    }

    private Set<String> collectBatchOperationIds(SyncDocumentRequest request) {
        Set<String> operationIds = new HashSet<>();

        for (SyncOperationRequest operation : request.pendingOperations()) {
            if (operation != null &&
                    operation.operationId() != null &&
                    !operation.operationId().isBlank()) {
                operationIds.add(operation.operationId());
            }
        }

        return operationIds;
    }

    // =========================================================
    // Shared persistence
    // =========================================================

    private OperationResponse persistOperationAndUpdateDocument(
            CollaborativeDocument document,
            OperationRequest operationToApply,
            long originalBaseVersion
    ) {
        String updatedContent = documentStateService.applyOperation(
                document.getCurrentContent(),
                operationToApply.getType(),
                operationToApply.getPosition(),
                operationToApply.getText(),
                operationToApply.getLength()
        );

        long nextServerVersion = document.getCurrentVersion() + 1;
        Instant now = Instant.now();

        DocumentOperation operation = new DocumentOperation(
                operationToApply.getOperationId(),
                document.getId(),
                operationToApply.getClientId(),
                originalBaseVersion,
                nextServerVersion,
                operationToApply.getType(),
                operationToApply.getPosition(),
                operationToApply.getText(),
                operationToApply.getLength(),
                now
        );

        DocumentOperation savedOperation = operationRepository.save(operation);

        document.setCurrentContent(updatedContent);
        document.setCurrentVersion(nextServerVersion);
        document.setUpdatedAt(now);

        CollaborativeDocument savedDocument = documentRepository.save(document);

        return toResponse(savedOperation, savedDocument.getCurrentContent());
    }

    // =========================================================
    // Operation validation and safety normalization
    // =========================================================

    private void validateCommonRequest(OperationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Operation request is required");
        }

        if (request.getOperationId() == null || request.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Operation id is required");
        }

        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new IllegalArgumentException("Client id is required");
        }

        if (request.getBaseVersion() < 0) {
            throw new IllegalArgumentException("Base version cannot be negative");
        }

        if (request.getType() == null) {
            throw new IllegalArgumentException("Operation type is required");
        }

        if (request.getType() == OperationType.NO_OP) {
            throw new IllegalArgumentException("Client cannot send NO_OP operation directly");
        }

        if (request.getType() == OperationType.REVERT) {
            throw new IllegalArgumentException("Client cannot send REVERT through operation API");
        }

        validateOperationShape(
                request.getType(),
                request.getPosition(),
                request.getText(),
                request.getLength()
        );
    }

    private void validateOperationShape(
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        if (position < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        if (type == OperationType.INSERT) {
            if (text == null || text.isEmpty()) {
                throw new IllegalArgumentException("Insert text is required");
            }
            return;
        }

        if (type == OperationType.DELETE) {
            if (length == null || length <= 0) {
                throw new IllegalArgumentException("Delete length must be greater than zero");
            }
        }
    }

    private OperationRequest normalizeOperationForCurrentContent(
            OperationRequest operation,
            String currentContent
    ) {
        String safeContent = safeContent(currentContent);
        int contentLength = safeContent.length();

        if (operation.getType() == OperationType.NO_OP) {
            return operation;
        }

        if (operation.getType() == OperationType.INSERT) {
            int safePosition = operation.getPosition();

            if (safePosition < 0) {
                safePosition = 0;
            }

            if (safePosition > contentLength) {
                safePosition = contentLength;
            }

            if (safePosition == operation.getPosition()) {
                return operation;
            }

            return copyOperation(
                    operation,
                    OperationType.INSERT,
                    safePosition,
                    operation.getText(),
                    null
            );
        }

        if (operation.getType() == OperationType.DELETE) {
            if (contentLength == 0 || operation.getPosition() >= contentLength) {
                return copyOperation(operation, OperationType.NO_OP, contentLength, null, null);
            }

            int safePosition = Math.max(0, operation.getPosition());
            int requestedLength = operation.getLength() == null ? 0 : operation.getLength();

            if (requestedLength <= 0) {
                return copyOperation(operation, OperationType.NO_OP, safePosition, null, null);
            }

            int maxAvailableLength = contentLength - safePosition;
            int safeLength = Math.min(requestedLength, maxAvailableLength);

            if (safeLength <= 0) {
                return copyOperation(operation, OperationType.NO_OP, safePosition, null, null);
            }

            if (safePosition == operation.getPosition() &&
                    safeLength == requestedLength) {
                return operation;
            }

            return copyOperation(operation, OperationType.DELETE, safePosition, null, safeLength);
        }

        return operation;
    }

    private OperationRequest copyOperation(
            OperationRequest source,
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        return new OperationRequest(
                source.getOperationId(),
                source.getClientId(),
                source.getBaseVersion(),
                type,
                position,
                text,
                length
        );
    }

    private boolean containsRevertOperation(List<DocumentOperation> operations) {
        return operations.stream()
                .anyMatch(operation -> operation.getType() == OperationType.REVERT);
    }

    // =========================================================
    // Phase 6: Document History
    // =========================================================

    public List<OperationHistoryResponse> getDocumentHistory(String documentId) {
        getDocumentOrThrow(documentId);

        return operationRepository.findByDocumentIdOrderByServerVersionAsc(documentId)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    // =========================================================
    // Phase 6 + Phase 7: Get Document at Version / Replay
    // =========================================================

    public DocumentVersionResponse getDocumentAtVersion(
            String documentId,
            long targetVersion
    ) {
        CollaborativeDocument document = getDocumentOrThrow(documentId);

        validateTargetVersion(document, targetVersion);

        String content = rebuildDocumentContentAtVersion(document, targetVersion);

        return new DocumentVersionResponse(
                documentId,
                targetVersion,
                content
        );
    }

    private String rebuildDocumentContentAtVersion(
            CollaborativeDocument document,
            long targetVersion
    ) {
        String content = safeContent(document.getInitialContent());

        List<DocumentOperation> operations =
                operationRepository.findByDocumentIdOrderByServerVersionAsc(document.getId());

        for (DocumentOperation operation : operations) {
            if (operation.getServerVersion() > targetVersion) {
                break;
            }

            if (operation.getType() == OperationType.REVERT) {
                content = safeContent(operation.getContentAfterRevert());
            } else {
                content = documentStateService.applyOperation(
                        content,
                        operation.getType(),
                        operation.getPosition(),
                        operation.getText(),
                        operation.getLength()
                );
            }
        }

        return content;
    }

    private void validateTargetVersion(
            CollaborativeDocument document,
            long targetVersion
    ) {
        if (targetVersion < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }

        if (targetVersion > document.getCurrentVersion()) {
            throw new IllegalArgumentException(
                    "Requested version " + targetVersion +
                            " is greater than current version " +
                            document.getCurrentVersion()
            );
        }
    }

    // =========================================================
    // Phase 7: Revert Document
    // =========================================================

    public RevertDocumentResponse revertDocument(
            String documentId,
            RevertDocumentRequest request
    ) {
        Object lock = documentLockManager.getLock(documentId);

        synchronized (lock) {
            return revertDocumentInternal(documentId, request);
        }
    }

    private RevertDocumentResponse revertDocumentInternal(
            String documentId,
            RevertDocumentRequest request
    ) {
        validateRevertRequest(request);

        CollaborativeDocument document = getDocumentOrThrow(documentId);

        Optional<DocumentOperation> existingOperation =
                operationRepository.findByDocumentIdAndClientIdAndOperationId(
                        documentId,
                        request.getClientId(),
                        request.getOperationId()
                );

        if (existingOperation.isPresent()) {
            DocumentOperation existing = existingOperation.get();

            if (existing.getType() != OperationType.REVERT) {
                throw new IllegalArgumentException(
                        "Operation id already used for a non-revert operation."
                );
            }

            return toRevertResponse(existing);
        }

        if (request.getBaseVersion() != document.getCurrentVersion()) {
            throw new IllegalArgumentException(
                    "Base version mismatch for revert. Client baseVersion: " +
                            request.getBaseVersion() +
                            ", server currentVersion: " +
                            document.getCurrentVersion()
            );
        }

        validateTargetVersion(document, request.getTargetVersion());

        String targetVersionContent =
                rebuildDocumentContentAtVersion(document, request.getTargetVersion());

        long nextServerVersion = document.getCurrentVersion() + 1;
        Instant now = Instant.now();

        DocumentOperation revertOperation = new DocumentOperation();
        revertOperation.setOperationId(request.getOperationId());
        revertOperation.setDocumentId(documentId);
        revertOperation.setClientId(request.getClientId());
        revertOperation.setBaseVersion(request.getBaseVersion());
        revertOperation.setServerVersion(nextServerVersion);
        revertOperation.setType(OperationType.REVERT);

        revertOperation.setPosition(0);
        revertOperation.setText(null);
        revertOperation.setLength(null);

        revertOperation.setRevertedToVersion(request.getTargetVersion());
        revertOperation.setContentAfterRevert(targetVersionContent);
        revertOperation.setCreatedAt(now);

        DocumentOperation savedOperation = operationRepository.save(revertOperation);

        document.setCurrentContent(targetVersionContent);
        document.setCurrentVersion(nextServerVersion);
        document.setUpdatedAt(now);

        documentRepository.save(document);

        return toRevertResponse(savedOperation);
    }

    // =========================================================
    // Sync validation
    // =========================================================

    private void validateSyncRequest(SyncDocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Sync request is required");
        }

        if (request.clientId() == null || request.clientId().isBlank()) {
            throw new IllegalArgumentException("Client id is required");
        }

        if (request.lastKnownServerVersion() < 0) {
            throw new IllegalArgumentException("Last known server version cannot be negative");
        }

        if (request.pendingOperations() == null || request.pendingOperations().isEmpty()) {
            throw new IllegalArgumentException("Pending operations cannot be empty");
        }
    }

    private void validateSyncOperation(SyncOperationRequest operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Pending operation cannot be null");
        }

        if (operation.operationId() == null || operation.operationId().isBlank()) {
            throw new IllegalArgumentException("Operation id is required");
        }

        if (operation.baseVersion() < 0) {
            throw new IllegalArgumentException("Base version cannot be negative");
        }

        if (operation.type() == null) {
            throw new IllegalArgumentException("Operation type is required");
        }

        if (operation.type() == OperationType.NO_OP) {
            throw new IllegalArgumentException("Client cannot send NO_OP operation directly");
        }

        if (operation.type() == OperationType.REVERT) {
            throw new IllegalArgumentException("Client cannot send REVERT through sync API. Use revert API.");
        }

        validateOperationShape(
                operation.type(),
                operation.position(),
                operation.text(),
                operation.length()
        );
    }

    private void validateRevertRequest(RevertDocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Revert request is required");
        }

        if (request.getOperationId() == null || request.getOperationId().isBlank()) {
            throw new IllegalArgumentException("Operation id is required");
        }

        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new IllegalArgumentException("Client id is required");
        }

        if (request.getBaseVersion() < 0) {
            throw new IllegalArgumentException("Base version cannot be negative");
        }

        if (request.getTargetVersion() < 0) {
            throw new IllegalArgumentException("Target version cannot be negative");
        }
    }

    // =========================================================
    // Helpers / Mappers
    // =========================================================

    private CollaborativeDocument getDocumentOrThrow(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private String safeContent(String content) {
        return content == null ? "" : content;
    }

    private OperationResponse toResponse(
            DocumentOperation operation,
            String currentContent
    ) {
        return new OperationResponse(
                operation.getDocumentId(),
                operation.getOperationId(),
                operation.getClientId(),
                operation.getBaseVersion(),
                operation.getServerVersion(),
                operation.getType(),
                operation.getPosition(),
                operation.getText(),
                operation.getLength(),
                safeContent(currentContent),
                operation.getCreatedAt()
        );
    }

    private OperationHistoryResponse toHistoryResponse(DocumentOperation operation) {
        return new OperationHistoryResponse(
                operation.getOperationId(),
                operation.getClientId(),
                operation.getBaseVersion(),
                operation.getServerVersion(),
                operation.getType(),
                operation.getPosition(),
                operation.getText(),
                operation.getLength(),
                operation.getCreatedAt()
        );
    }

    private RevertDocumentResponse toRevertResponse(DocumentOperation operation) {
        return new RevertDocumentResponse(
                operation.getDocumentId(),
                operation.getOperationId(),
                operation.getClientId(),
                operation.getBaseVersion(),
                operation.getRevertedToVersion() == null ? 0L : operation.getRevertedToVersion(),
                operation.getServerVersion(),
                safeContent(operation.getContentAfterRevert()),
                operation.getCreatedAt()
        );
    }
}
