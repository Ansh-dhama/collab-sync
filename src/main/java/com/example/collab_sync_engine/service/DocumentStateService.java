package com.example.collab_sync_engine.service;

import com.example.collab_sync_engine.Enum.OperationType;
import org.springframework.stereotype.Service;

@Service
public class DocumentStateService {

    public String applyOperation(
            String currentContent,
            OperationType type,
            int position,
            String text,
            Integer length
    ) {
        String safeContent = currentContent == null ? "" : currentContent;

        if (type == null) {
            throw new IllegalArgumentException("Operation type is required");
        }

        if (type == OperationType.NO_OP) {
            return safeContent;
        }

        if (type == OperationType.INSERT) {
            return applyInsert(safeContent, position, text);
        }

        if (type == OperationType.DELETE) {
            return applyDelete(safeContent, position, length);
        }

        throw new IllegalArgumentException("Invalid operation type: " + type);
    }

    public String applyInsert(String currentContent, int position, String text) {
        String safeContent = currentContent == null ? "" : currentContent;

        // Space, tab and newline are valid text. Only null/empty is invalid.
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Insert text is required");
        }

        if (position < 0 || position > safeContent.length()) {
            throw new IllegalArgumentException(
                    "Invalid insert position: " + position +
                            ". Content length is: " + safeContent.length()
            );
        }

        return safeContent.substring(0, position)
                + text
                + safeContent.substring(position);
    }

    public String applyDelete(String currentContent, int position, Integer length) {
        String safeContent = currentContent == null ? "" : currentContent;

        if (length == null || length <= 0) {
            throw new IllegalArgumentException("Delete length must be greater than zero");
        }

        if (position < 0 || position >= safeContent.length()) {
            throw new IllegalArgumentException(
                    "Invalid delete position: " + position +
                            ". Content length is: " + safeContent.length()
            );
        }

        int endPosition = position + length;

        if (endPosition > safeContent.length()) {
            throw new IllegalArgumentException(
                    "Delete range is invalid. Position: " + position +
                            ", length: " + length +
                            ", content length: " + safeContent.length()
            );
        }

        return safeContent.substring(0, position)
                + safeContent.substring(endPosition);
    }
}
