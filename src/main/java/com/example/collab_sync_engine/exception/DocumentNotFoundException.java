package com.example.collab_sync_engine.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String documentId) {
        super("Document not found with id: " + documentId);
    }
}