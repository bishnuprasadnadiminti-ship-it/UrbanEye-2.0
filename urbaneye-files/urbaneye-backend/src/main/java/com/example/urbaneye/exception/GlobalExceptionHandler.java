package com.example.urbaneye.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FreeTierExhaustedException.class)
    public ResponseEntity<?> handleFreeTierExhaustedException(FreeTierExhaustedException ex) {
        return ResponseEntity.status(409).body(Map.of(
            "fallback", true,
            "error", "AI quota exhausted",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(500).body(Map.of(
            "error", "Internal Server Error",
            "message", ex.getClass().getSimpleName() + ": " + ex.getMessage()
        ));
    }
}

