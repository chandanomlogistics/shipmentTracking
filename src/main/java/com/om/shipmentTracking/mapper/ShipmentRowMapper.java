package com.om.shipmentTracking.mapper;

import com.om.shipmentTracking.dto.Shipment;
import com.om.shipmentTracking.dto.ShipmentStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ShipmentRowMapper implements RowMapper<Shipment>  {

    @Override
    public Shipment mapRow(ResultSet rs, int rowNum) throws SQLException {

        ShipmentStatus status = ShipmentStatus.builder()
                .Status(rs.getString("STATUS"))                       // e.g. 'Undelivered'
                .StatusDateTime(rs.getString("STATUSDATETIME"))       // e.g. '2025-11-13T10:40:47'
                .StatusType(rs.getString("STATUSTYPE"))               // ' '
                .StatusLocation(rs.getString("STATUSLOCATION"))       // 'OUT FOR DELIVERY'
                .StatusLatitude(rs.getString("STATUSLATITUDE"))       // ' '
                .StatusLongitude(rs.getString("STATUSLONGITUDE"))     // ' '
                .ScanCode(rs.getString("SCANCODE"))                   // 'E4'
                .Scan(rs.getString("SCAN"))                           // 'EPIDEMIC - CUSTOMER CLOSURE, DELAY'
                .build();

        return Shipment.builder()
                .SenderID(rs.getString("SENDERID"))                   // 'OM'
                .PickUpDate(rs.getString("PICK_UP_DATE"))             // '2025-10-23T21:32:29'
                .Origin(rs.getString("ORIGIN"))                       // 'LENOVO BANGLORE HUB-KARNATAKA'
                .Destination(rs.getString("DESTINATION"))             // 'PONDICHERY-TAMILNADU'
                .ReferenceNo(rs.getString("REFERENCENO"))             // NULL
                .AWB(String.valueOf(rs.getLong("AWB")))                             // '12550001114'
                .AWBValidityDate(rs.getString("AWBVALIDITYDATE"))     // '2025-11-13'
                .ShipmentMode(rs.getString("SHIPMENTMODE"))           // 'S'
                .ExpectedDeliveryDate(rs.getString("EXPECTEDDELIVERYDATE")) // '2025-10-27T21:32:29'
                .Weight(String.valueOf(rs.getFloat("WEIGHT")))                       // '10'
                .ReceivedBy(rs.getString("RECEIVEDBY"))               // NULL
                .Attempt_count(rs.getString("ATTEMPT_COUNT"))         // NULL
                .PodImage(rs.getString("PODIMAGE"))                   // NULL
                .Status(status)
                .build();
    }

}
