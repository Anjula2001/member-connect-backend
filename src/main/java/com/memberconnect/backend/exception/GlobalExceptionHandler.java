package com.memberconnect.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handle AccessDeniedException and return a 403 Forbidden response
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You do not have permission to perform this action."));
    }

    // Handle ResponseStatusException and return the appropriate status and message
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Request failed";
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("message", message));
    }

    // Handle MethodArgumentNotValidException and return a 400 Bad Request response with field errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            // First message wins: a field with several failed constraints should not
            // overwrite the most specific message with a later, vaguer one.
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "message", "Please correct the highlighted fields.",
                        "errors", fieldErrors
                ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Request failed";
        return ResponseEntity
                .badRequest()
                .body(Map.of("message", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("message", "File is too large to upload."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Unexpected server error";
        return ResponseEntity
                .internalServerError()
                .body(Map.of("message", message));
    }
}