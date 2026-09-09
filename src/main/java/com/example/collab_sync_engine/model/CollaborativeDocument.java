package com.example.collab_sync_engine.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "documents")
public class CollaborativeDocument {

    @Id
    private String id;

    private String title;

    private String initialContent;

    private String currentContent;

    private long currentVersion;

    private String createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    public CollaborativeDocument() {
    }

    public CollaborativeDocument(
            String title,
            String initialContent,
            String currentContent,
            long currentVersion,
            String createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.title = title;
        this.initialContent = initialContent == null ? "" : initialContent;
        this.currentContent = currentContent == null ? "" : currentContent;
        this.currentVersion = currentVersion;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getInitialContent() {
        return initialContent;
    }

    public String getCurrentContent() {
        return currentContent;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent == null ? "" : initialContent;
    }

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent == null ? "" : currentContent;
    }

    public void setCurrentVersion(long currentVersion) {
        this.currentVersion = currentVersion;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
