package com.om.shipmentTracking.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiResponse {

    private boolean success;
    private Object body;
    private LocalDateTime timestamp;

    public static ApiResponse success(String message) {
        return ApiResponse.builder()
                .success(true)
                .body(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ApiResponse error(String message) {
        return ApiResponse.builder()
                .success(false)
                .body(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}