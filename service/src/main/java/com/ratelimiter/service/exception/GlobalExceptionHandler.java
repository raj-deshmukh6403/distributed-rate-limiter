package com.ratelimiter.service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleApiKeyNotFound(
            ApiKeyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error",     "API_KEY_NOT_FOUND",
                "message",   ex.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error",     "INVALID_REQUEST",
                "message",   ex.getMessage(),
                "timestamp", Instant.now().toEpochMilli()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        log.error("Unexpected error: ", ex);  // this will print the full stack trace
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error",     "INTERNAL_ERROR",
                "message",   ex.getMessage(),    // also return the real message
                "timestamp", Instant.now().toEpochMilli()
        ));
    }
}