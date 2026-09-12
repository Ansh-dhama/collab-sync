package com.example.collab_sync_engine.model;

import com.example.collab_sync_engine.Enum.OperationType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "document_operations")
@CompoundIndexes({
        @CompoundIndex(
                name = "unique_operation_per_client_document",
                def = "{'documentId': 1, 'clientId': 1, 'operationId': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "document_server_version_idx",
                def = "{'documentId': 1, 'serverVersion': 1}"
        )
})
public class DocumentOperation {

    @Id
    private String id;

    private String operationId;

    private String documentId;

    private String clientId;

    private long baseVersion;

    private long serverVersion;

    private OperationType type;

    private int position;

    private String text;

    private Integer length;

    private Instant createdAt;

    private Long revertedToVersion;

    private String contentAfterRevert;

    public DocumentOperation() {
    }

    public DocumentOperation(
            String operationId,
            String documentId,
            String clientId,
            long baseVersion,
            long serverVersion,
            OperationType type,
            int position,
            String text,
            Integer length,
            Instant createdAt
    ) {
        this.operationId = operationId;
        this.documentId = documentId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.serverVersion = serverVersion;
        this.type = type;
        this.position = position;
        this.text = text;
        this.length = length;
        this.createdAt = createdAt;
    }
  public  Long getRevertedToVersion() {
        return revertedToVersion;
  }
  public void setRevertedToVersion(Long revertedToVersion) {
        this.revertedToVersion = revertedToVersion;
  }
  public String getContentAfterRevert() {
        return contentAfterRevert;
  }
  public void setContentAfterRevert(String contentAfterRevert) {
        this.contentAfterRevert = contentAfterRevert;
  }
    public String getId() {
        return id;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getDocumentId() {
        return documentId;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
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

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}