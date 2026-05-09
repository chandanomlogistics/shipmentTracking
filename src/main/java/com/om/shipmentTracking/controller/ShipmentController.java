package com.om.shipmentTracking.controller;

import com.om.shipmentTracking.dto.ApiResponse;
import com.om.shipmentTracking.dto.ImageRequest;
import com.om.shipmentTracking.service.ConverterService;
import com.om.shipmentTracking.service.ShipmentSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/lenovo/shipments")
@RequiredArgsConstructor
@Slf4j
public class ShipmentController {

    private final ShipmentSyncService shipmentSyncService;
    private final ConverterService converterService;

    /**
     *  Trigger manual sync (multithreaded processing)
     */
    @GetMapping("/sync")
    public ResponseEntity<ApiResponse> triggerShipmentSync() {

        log.info("[CONTROLLER] Manual shipment sync triggered at {}", LocalDateTime.now());

        try {
            shipmentSyncService.processShipments();

            return ResponseEntity.ok(
                    ApiResponse.success("Shipment sync started successfully")
            );

        } catch (Exception e) {

            log.error("[CONTROLLER] Failed to trigger shipment sync", e);

            return ResponseEntity.internalServerError().body(
                    ApiResponse.error("Failed to start shipment sync")
            );
        }
    }

    /**
     *  Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse> healthCheck() {
        return ResponseEntity.ok(
                ApiResponse.success("Shipment service is running")
        );
    }

    @PostMapping("/getByteImg")
    public ResponseEntity<Object> getByteImg(@RequestBody ImageRequest request) {
        String value = converterService.convertToPdfBase64(request.getUrl());
//        log.info("[CONTROLLER] Get byte-img image {} ", value);
        return ResponseEntity.ok(value);
    }
}