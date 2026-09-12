package com.example.collab_sync_engine.exception;

import com.mongodb.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "Document Not Found", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException exception) {
        Map<String, Object> error = baseError(HttpStatus.BAD_REQUEST, "Validation Failed");

        Map<String, String> fieldErrors = new HashMap<>();
        exception.getBindingResult()
                .getFieldErrors()
                .forEach(fieldError -> fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));

        error.put("fieldErrors", fieldErrors);
        error.put("message", "Request validation failed.");

        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidJsonOrEnum(HttpMessageNotReadableException exception) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Invalid Request Body",
                "Invalid JSON format or invalid enum value. Operation type must be INSERT or DELETE."
        );
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKeyException(DuplicateKeyException exception) {
        return error(HttpStatus.CONFLICT, "Duplicate Operation", "This operation has already been processed.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleStaticResourceNotFound(NoResourceFoundException exception) {
        /*
         * Missing favicon/assets should be a 404, not a backend 500.
         * This makes frontend build/copy mistakes easier to understand.
         */
        return error(HttpStatus.NOT_FOUND, "Static Resource Not Found", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception exception) {
        log.error("Unhandled exception occurred", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Something went wrong. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String error, String message) {
        Map<String, Object> body = baseError(status, error);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> baseError(HttpStatus status, String error) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", error);
        return body;
    }
}
