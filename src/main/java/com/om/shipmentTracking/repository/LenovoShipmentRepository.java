package com.om.shipmentTracking.repository;

import com.om.shipmentTracking.dto.Shipment;
import com.om.shipmentTracking.mapper.ShipmentRowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Slf4j
public class LenovoShipmentRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertEwbLog(Long ewbNo) {
        String sql = "INSERT INTO EWB_LOG (EWB_NO, EWB_SEND_TIME, EXTEND_TYPE) VALUES (?, SYSDATE, 'AUTO JAVA')";
        try {
            jdbcTemplate.update(sql, ewbNo);
            log.info("EWB log saved successfully for EWB No: {}", ewbNo);
        } catch (Exception e) {
            log.error("Failed to insert EWB log for EWB No {}: {}", ewbNo, e.getMessage());
        }
    }

    public List<Shipment> fetchShipments() {
        String sql = "SELECT * FROM VW_LENOVO_TRACKING_DATA A WHERE NOT EXISTS " +
                "(SELECT 'X' FROM lenovo_stop_push_data B WHERE B.lr_no = A.AWB AND B.EVENT_TYPE = A.STATUS " +
                "AND B.DOCUMENT_TYPE = CASE WHEN A.STATUS = 'Delivered' AND A.PodImage IS NULL THEN 'POD NOT AVAILBLE' " +
                "WHEN A.STATUS = 'Delivered' AND A.PodImage IS NOT NULL THEN 'POD PUSHED' ELSE  STATUSLOCATION END) AND A.AWB = 12530015845";
        return jdbcTemplate.query(sql, new ShipmentRowMapper());
    }

    /**
     * Saves Lenovo API POD log details directly to DB
     *
     * @param statusCode  HTTP status code from API
     * @param cnNumber    CN or shipment number
     * @param requestJson Original request JSON (can be large)
     * @param message     Log or error message
     */
    public void saveLenovoPodLogMessage(Integer statusCode, String cnNumber, String requestJson, String message) {
        try {
            String sql = """
                INSERT INTO pod_log_table (type, status_code, cn_number, request_json, message)
                VALUES ('Lenovo_Api', ?, ?, ?, ?)
            """;

            jdbcTemplate.update(sql, statusCode, cnNumber, requestJson, message);

            log.info("Logged Lenovo API message to DB | CN={} | Status={} | Message={}",
                    cnNumber, statusCode, message);

        } catch (Exception e) {
            log.error("Failed to log Lenovo API message | CN={} | Error={}", cnNumber, e.getMessage(), e);
        }
    }

   public void insertStopPush(String awb, String docType, String payload, String status){
       try {
           String sql = """
            INSERT INTO LENOVO_STOP_PUSH_DATA (LR_NO, DOCUMENT_TYPE, PAYLOAD, EVENT_TYPE)
            VALUES (:awb, :docType, :payload, :status)
            """;
           jdbcTemplate.update(sql, awb, docType, payload, status);
           log.info("Inserted stop push data for AWB: {}, Document Type: {}, Status: {}", awb, docType, status);
       } catch (Exception e) {
           log.error("Failed to insert stop push data for AWB: {}, Document Type: {}, Status: {}. Error: {}",
                   awb, docType, status, e.getMessage(), e);
       }
   }
}