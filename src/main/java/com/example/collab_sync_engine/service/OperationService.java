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
import java.util.List;
import java.util.Optional;

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
    // Phase 3 + Phase 5: Apply INSERT / DELETE Operation
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

        CollaborativeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

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

            /*
             * Safe rule:
             * If a REVERT happened after client's baseVersion,
             * then old INSERT/DELETE cannot be safely transformed across that reset.
             * Client should refresh latest document and send operation again.
             */
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
                request.getOperationId(),
                documentId,
                request.getClientId(),
                request.getBaseVersion(),
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

    private boolean containsRevertOperation(List<DocumentOperation> operations) {
        return operations.stream()
                .anyMatch(operation -> operation.getType() == OperationType.REVERT);
    }

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

        if (request.getPosition() < 0) {
            throw new IllegalArgumentException("Position cannot be negative");
        }

        if (request.getType() == OperationType.INSERT) {
            if (request.getText() == null || request.getText().isEmpty()) {
                throw new IllegalArgumentException("Insert text is required");
            }
        }

        if (request.getType() == OperationType.DELETE) {
            if (request.getLength() == null || request.getLength() <= 0) {
                throw new IllegalArgumentException("Delete length must be greater than zero");
            }
        }
    }
    // =========================================================
    // Phase 6: Document History
    // =========================================================

    public List<OperationHistoryResponse> getDocumentHistory(String documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

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
        CollaborativeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        validateTargetVersion(document, targetVersion);

        String content = rebuildDocumentContentAtVersion(document, targetVersion);

        return new DocumentVersionResponse(
                documentId,
                targetVersion,
                content
        );
    }
    public SyncDocumentResponse syncPendingOperations(
            String documentId,
            SyncDocumentRequest request
    ) {
        validateSyncRequest(request);

        Object lock = documentLockManager.getLock(documentId);

        synchronized (lock) {

            documentRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));

            List<SyncOperationResult> results = new ArrayList<>();

            boolean stopProcessing = false;

            for (SyncOperationRequest pendingOperation : request.pendingOperations()) {

                if (stopProcessing) {
                    results.add(new SyncOperationResult(
                            pendingOperation.operationId(),
                            SyncOperationStatus.SKIPPED,
                            null,
                            "Skipped because previous operation failed"
                    ));
                    continue;
                }

                try {
                    validateSyncOperation(pendingOperation);

                    Optional<DocumentOperation> existingOperation =
                            operationRepository.findByDocumentIdAndClientIdAndOperationId(
                                    documentId,
                                    request.clientId(),
                                    pendingOperation.operationId()
                            );

                    boolean duplicate = existingOperation.isPresent();

                    OperationRequest operationRequest = toOperationRequest(
                            request.clientId(),
                            pendingOperation
                    );

                    OperationResponse operationResponse =
                            applyOperationInternal(documentId, operationRequest);

                    SyncOperationStatus status = duplicate
                            ? SyncOperationStatus.DUPLICATE
                            : SyncOperationStatus.APPLIED;

                    String message = duplicate
                            ? "Operation already applied earlier"
                            : "Operation applied successfully";

                    results.add(new SyncOperationResult(
                            pendingOperation.operationId(),
                            status,
                            operationResponse.getServerVersion(),
                            message
                    ));

                } catch (Exception ex) {

                    results.add(new SyncOperationResult(
                            pendingOperation.operationId(),
                            SyncOperationStatus.FAILED,
                            null,
                            ex.getMessage()
                    ));

                    stopProcessing = true;
                }
            }

            CollaborativeDocument latestDocument = documentRepository.findById(documentId)
                    .orElseThrow(() -> new DocumentNotFoundException(documentId));

            return new SyncDocumentResponse(
                    documentId,
                    request.clientId(),
                    latestDocument.getCurrentVersion(),
                    latestDocument.getCurrentContent(),
                    results
            );
        }
    }
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
    }private void validateSyncOperation(SyncOperationRequest operation) {
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
    }
    private String rebuildDocumentContentAtVersion(
            CollaborativeDocument document,
            long targetVersion
    ) {
        String content = document.getInitialContent() == null
                ? ""
                : document.getInitialContent();

        List<DocumentOperation> operations =
                operationRepository.findByDocumentIdOrderByServerVersionAsc(document.getId());

        for (DocumentOperation operation : operations) {
            if (operation.getServerVersion() > targetVersion) {
                break;
            }

            if (operation.getType() == OperationType.REVERT) {
                content = operation.getContentAfterRevert() == null
                        ? ""
                        : operation.getContentAfterRevert();
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

        CollaborativeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

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
    // Mappers
    // =========================================================

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
                currentContent,
                operation.getCreatedAt()
        );
    }
    private OperationRequest toOperationRequest(
            String clientId,
            SyncOperationRequest pendingOperation
    ) {
        return new OperationRequest(
                pendingOperation.operationId(),
                clientId,
                pendingOperation.baseVersion(),
                pendingOperation.type(),
                pendingOperation.position(),
                pendingOperation.text(),
                pendingOperation.length()
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
                operation.getContentAfterRevert(),
                operation.getCreatedAt()
        );
    }
}