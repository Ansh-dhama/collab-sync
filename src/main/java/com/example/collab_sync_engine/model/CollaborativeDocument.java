package com.example.collab_sync_engine.model;


import jakarta.persistence.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "documents")
public class CollaborativeDocument {

    @Id
    private String id;

    private String title;

    private String currentContent;

    private long currentVersion;

    private String createdBy;

    private Instant createdAt;

    private Instant updatedAt;

    private String initialContent;

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
        this.initialContent = initialContent;
        this.currentContent = currentContent;
        this.currentVersion = currentVersion;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    public String getInitialContent() {
        return initialContent;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent;
    }
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
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

    public void setCurrentContent(String currentContent) {
        this.currentContent = currentContent;
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