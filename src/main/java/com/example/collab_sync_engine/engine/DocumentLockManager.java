package com.example.collab_sync_engine.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class DocumentLockManager {

    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();

    public Object getLock(String documentId) {
        return locks.computeIfAbsent(documentId, key -> new Object());
    }
}