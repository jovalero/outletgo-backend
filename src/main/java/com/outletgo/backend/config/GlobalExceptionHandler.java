package com.outletgo.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Captura errores de deserializacion de Jackson (fecha mal formateada, campo con tipo incorrecto, etc.)
     * que ocurren ANTES de que el controlador pueda manejarlos.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = ex.getMessage();
        String cause = ex.getCause() != null ? ex.getCause().getMessage() : "causa desconocida";

        log.error("=== JSON DESERIALIZATION ERROR ===");
        log.error("Message: {}", message);
        log.error("Cause: {}", cause);
        log.error("=================================");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "Error al leer el cuerpo de la solicitud",
                        "message", message != null ? message : "JSON invalido",
                        "cause", cause
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("=== UNHANDLED EXCEPTION ===");
        log.error("Type: {}", ex.getClass().getName());
        log.error("Message: {}", ex.getMessage());
        if (ex.getCause() != null) {
            log.error("Cause: {}", ex.getCause().getMessage());
        }
        log.error("===========================");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", ex.getClass().getSimpleName(),
                        "message", ex.getMessage() != null ? ex.getMessage() : "Error desconocido"
                ));
    }
}
