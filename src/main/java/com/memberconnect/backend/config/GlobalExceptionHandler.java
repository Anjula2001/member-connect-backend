package com.memberconnect.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Global exception handler – catches RuntimeExceptions thrown from any service
 * and turns them into a consistent JSON error response.
 *
 * Response body example:
 * {
 *   "timestamp": "2025-05-04T10:30:00",
 *   "status": 400,
 *   "message": "Member is not ACTIVE. Current status: INACTIVE"
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles all RuntimeExceptions thrown from service methods.
     * Returns HTTP 400 BAD REQUEST with the error message.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred"
                ));
    }

    /**
     * Catch-all for any other unexpected exceptions.
     * Returns HTTP 500 INTERNAL SERVER ERROR.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "message", "Internal server error: " + ex.getMessage()
                ));
    }
    /**
     * Handles validation errors from @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", LocalDateTime.now().toString(),
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "message", errorMsg
                ));
    }
}
