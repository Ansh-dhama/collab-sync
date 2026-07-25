package com.example.collab_sync_engine.dto;

public class DocumentVersionResponse {

    private String documentId;
    private long version;
    private String content;

    public DocumentVersionResponse() {
    }

    public DocumentVersionResponse(String documentId, long version, String content) {
        this.documentId = documentId;
        this.version = version;
        this.content = content;
    }

    public String getDocumentId() {
        return documentId;
    }

    public long getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public void setContent(String content) {
        this.content = content;
    }
}