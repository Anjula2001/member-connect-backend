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

    /**
     * A role check (@PreAuthorize) rejected the request. AccessDeniedException extends
     * RuntimeException, so without this more-specific handler it falls through to the
     * catch-all below and is reported as 400 Bad Request — which reads to the client as
     * "your input was wrong" rather than "you are not allowed".
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You do not have permission to perform this action."));
    }

    /**
     * Services throw ResponseStatusException to signal a specific status (404 NOT_FOUND,
     * 403 FORBIDDEN, ...). It also extends RuntimeException, so without this handler the
     * catch-all below flattened every one of them to 400 and leaked the intended status
     * into the message text (e.g. `400 {"message":"404 NOT_FOUND \"Member not found\""}`).
     * Honour the status the caller asked for, and return the bare reason as the message.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : "Request failed";
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("message", message));
    }

    /**
     * Bean validation on an @Valid @RequestBody failed.
     *
     * MethodArgumentNotValidException is a checked Exception, so without this handler it
     * fell through to the catch-all below and came back as 500 Internal Server Error
     * carrying Spring's raw binding dump — which reads to the client as "the server is
     * broken" rather than "this field is wrong", and put no usable message on the field.
     * Return 400 with the first message per field instead.
     */
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