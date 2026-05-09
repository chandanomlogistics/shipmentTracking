package com.om.shipmentTracking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.om.shipmentTracking.dto.*;
import com.om.shipmentTracking.repository.LenovoShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentSyncService {

    private final LenovoShipmentRepository lenovoShipmentRepository;
    private final RestTemplate restTemplate;
    private final ConverterService converterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("lenovoShipmentThreadPool")
    private ThreadPoolTaskExecutor threadPool;

    private static final String API_URL = "https://api-lis.lenovo.com/tspom/om";
    private static final String AUTH_TOKEN = "T00tcHJvOktESzQ5NDg1N0BkamZoag==";

    /**
     * Scheduler: runs every hour, 24 hours a day
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduleShipmentSync() {
        log.info("Starting scheduled Lenovo shipment sync at {}", LocalDateTime.now());
        try {
            processShipments();
        } catch (Exception e) {
            log.error("Error in scheduled Lenovo shipment sync", e);
        }
    }

    /**
     * Fetch shipments and send them concurrently to Lenovo API
     */
    public void processShipments() {
        List<Shipment> shipments = lenovoShipmentRepository.fetchShipments();

        if (shipments == null || shipments.isEmpty()) {
            log.info("No shipments found for Lenovo API sync.");
            return;
        }

        log.info("Fetched {} shipments for Lenovo API sync", shipments.size());

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        int total = shipments.size();

        for (Shipment shipment : shipments) {
            CompletableFuture
                    .runAsync(() -> processAndSend(shipment, successCount, failureCount, total), threadPool)
                    .exceptionally(throwable -> {
                        log.error("[Lenovo API] AWB={} | Async error: {}", shipment.getAWB(), throwable.getMessage());
                        failureCount.incrementAndGet();
                        return null;
                    });
        }
    }

    /**
     * Sends individual shipment and logs detailed response
     */
    private void processAndSend(Shipment shipment, AtomicInteger successCount, AtomicInteger failureCount, int total) {

        try {

            // Convert POD → Base64
            String bytePod = converterService.convertToPdfBase64(shipment.getPodImage());
            shipment.setPodImage(bytePod);

            log.info("[Lenovo API] Converted POD image to Base64 | AWB={} | POD Available={}",
                    shipment.getAWB(), shipment.getPodImage());

            ShipmentRequest request = ShipmentRequest.builder()
                    .shipment(shipment)
                    .build();

            // Lenovo expects an array, even for single shipment
            List<ShipmentRequest> payloadList = List.of(request);

            // Convert Shipment object to JSON
            String jsonBody = objectMapper.writeValueAsString(payloadList);

            // Build HTTP Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add(HttpHeaders.AUTHORIZATION, "Basic " + AUTH_TOKEN);

            // Prepare HTTP Request
            HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

            log.info("[Lenovo API] Sending AWB={} | Payload={}", shipment.getAWB(), jsonBody);

            // Execute POST request
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, requestEntity, String.class);

            // Log response
            if (response.getStatusCode().is2xxSuccessful() && isResponceSuccess(response.getBody())) {

                successCount.incrementAndGet();

                log.info("[Lenovo API] SUCCESS | AWB={} | Status={} | Response={} | Success={} | Failed={} | Total={}",
                        shipment.getAWB(), response.getStatusCode(), response.getBody(), successCount.get(), failureCount.get(), total);

                // Save STOP_PUSH (same as PHP)
                saveStopPushData(shipment, request);

                lenovoShipmentRepository.saveLenovoPodLogMessage(
                        200,
                        shipment.getAWB(),
                        jsonBody,
                        response.getBody()
                );

            } else {

                failureCount.incrementAndGet();

                log.warn("[Lenovo API] Status Failure | AWB={} | Status={} | Response={} | Success={} | Failed={} | Total={}",
                        shipment.getAWB(), response.getStatusCode(), response.getBody(), successCount.get(), failureCount.get(), total);

                lenovoShipmentRepository.saveLenovoPodLogMessage(
                        response.getStatusCode().value(),
                        shipment.getAWB(),
                        jsonBody,
                        response.getBody()
                );
            }

        } catch (JsonProcessingException e) {
            log.error("[Lenovo API] JSON ERROR | AWB={} | {}", shipment.getAWB(), e.getMessage());
            failureCount.incrementAndGet();

            lenovoShipmentRepository.saveLenovoPodLogMessage(
                    0,
                    shipment.getAWB(),
                    "Invalid JSON",
                    e.getMessage()
            );

        } catch (Exception e) {
            log.error("[Lenovo API] EXCEPTION | AWB={} | {}", shipment.getAWB(), e.getMessage(), e);
            failureCount.incrementAndGet();

            lenovoShipmentRepository.saveLenovoPodLogMessage(
                    0,
                    shipment.getAWB(),
                    safeJson(objectMapper, shipment),
                    e.getMessage()
            );
        }
    }

    private void saveStopPushData(Shipment shipment, ShipmentRequest request) {

        try {
            String payload = objectMapper.writeValueAsString(request);

            String status = shipment.getStatus().getStatus();

            String documentType =
                    "Delivered".equalsIgnoreCase(status)
                            ? (shipment.getPodImage() == null ? "POD NOT AVAILBLE" : "POD PUSHED")
                            : shipment.getStatus().getStatusLocation();

            log.info("Saving STOP_PUSH data | AWB={} | documentType={} | status={}",
                    shipment.getAWB(), documentType, status);

            lenovoShipmentRepository.insertStopPush(
                    shipment.getAWB(),
                    documentType,
                    payload,
                    status
            );

        } catch (Exception e) {
            log.error("STOP_PUSH insert failed | AWB={}", shipment.getAWB(), e);
        }
    }

    /**
     * Safely serialize object to JSON (avoids throwing from catch blocks)
     */
    private String safeJson(ObjectMapper mapper, Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{ \"error\": \"Failed to serialize request JSON\" }";
        }
    }

    private boolean isResponceSuccess(String body) {
        if (body == null || body.isBlank()) return false;

        try {
            JsonNode json = objectMapper.readTree(body);
            return "Success".equalsIgnoreCase(json.path("status").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    private void processShipmentBusinessValidation(Shipment shipment, String requestJson, String response) {

        ShipmentStatus status = shipment.getStatus();
        String statusStr = status.getStatus();
        String scanCode = status.getScanCode();

        log.info("[Lenovo API] Validation Check | AWB={} | status={} | scanCode={} | podImage={}",
                shipment.getAWB(), statusStr, scanCode, shipment.getPodImage());

        String finalMessage = null;

        if(shipment.getPodImage() == null && statusStr!= null && statusStr.equals("Delivered")){

            log.warn("[Lenovo API] AWB={} | POD image missing for Delivered shipment", shipment.getAWB());
            finalMessage = "POD image is missing for delivered shipment";

        } else if (statusStr!= null && statusStr.equals("Undelivered") && scanCode!=null && scanCode.equals("000")) {

            log.warn("[Lenovo API] AWB={} | Undelivered shipment with scan code 000", shipment.getAWB());
            finalMessage = "Undelivered shipment with scan code 000";

        } else if (shipment.getExpectedDeliveryDate()== null && shipment.getPickUpDate() != null) {

            log.warn("[Lenovo API] AWB={} | EDD is missing but pickup date is present", shipment.getAWB());
            finalMessage = "EDD is missing";

        } else if(status.getStatusLocation() != null && status.getStatusLocation().equals("OUT FOR DELIVERY")){

            log.warn("[Lenovo API] AWB={} | Shipment still Out for Delivery", shipment.getAWB());
            finalMessage = "Shipment is still out for delivery";

        }else {
            finalMessage = response;
        }

        lenovoShipmentRepository.saveLenovoPodLogMessage(
                200,
                shipment.getAWB(),
                requestJson,
                finalMessage
        );
    }
}