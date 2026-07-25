package com.example.collab_sync_engine.repository;

import com.example.collab_sync_engine.model.CollaborativeDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentRepository
        extends MongoRepository<CollaborativeDocument, String> {
  List<CollaborativeDocument> findAllByOrderByUpdatedAtDesc();
}