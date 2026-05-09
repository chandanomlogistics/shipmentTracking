package com.om.shipmentTracking.exceptionHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.om.shipmentTracking.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.sql.SQLException;
import java.time.LocalDateTime;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {

        log.error("[GLOBAL] Unhandled Exception: {}", ex.getMessage(), ex);

        return buildResponse(
                "INTERNAL_SERVER_ERROR",
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ================= JSON =================

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorResponse> handleJson(JsonProcessingException ex) {

        log.error("[JSON ERROR] {}", ex.getMessage(), ex);

        return buildResponse(
                "JSON_ERROR",
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ================= REST / API =================

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<ErrorResponse> handleHttpStatus(HttpStatusCodeException ex) {

        log.error("[API ERROR] Status={} Body={}",
                ex.getStatusCode(), ex.getResponseBodyAsString());

        return buildResponse(
                "API_ERROR",
                ex.getResponseBodyAsString(),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClient(RestClientException ex) {

        log.error("[REST CLIENT ERROR] {}", ex.getMessage(), ex);

        return buildResponse(
                "EXTERNAL_API_FAILURE",
                ex.getMessage(),
                HttpStatus.BAD_GATEWAY
        );
    }

    // ================= DATABASE =================
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> handleSql(SQLException ex) {

        log.error("[SQL ERROR] {}", ex.getMessage(), ex);

        return buildResponse(
                "SQL_ERROR",
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ================= VALIDATION =================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {

        log.warn("[INVALID INPUT] {}", ex.getMessage());

        return buildResponse(
                "INVALID_INPUT",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST
        );
    }

    // ================= COMMON BUILDER =================

    private ResponseEntity<ErrorResponse> buildResponse(String code,
                                                        String message,
                                                        HttpStatus status) {

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .errorCode(code)
                .message(message)
                .status(status.value())
                .build();

        return new ResponseEntity<>(error, status);
    }
}