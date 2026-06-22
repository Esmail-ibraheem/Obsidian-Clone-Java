package com.obsidianclone.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.obsidianclone.vault.NoteContent;
import com.obsidianclone.vault.VaultConflictException;
import com.obsidianclone.vault.VaultException;
import com.obsidianclone.vault.VaultNotFoundException;

/** Maps vault exceptions to HTTP responses (400 / 404 / 409). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(VaultConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(VaultConflictException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        NoteContent current = e.getCurrent();
        if (current != null) {
            body.put("currentContent", current.content());
            body.put("currentMtime", current.mtime());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(VaultNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(VaultNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(VaultException.class)
    public ResponseEntity<Map<String, Object>> badRequest(VaultException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** Malformed/unreadable request body -> 400 rather than a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Malformed request body"));
    }
}
