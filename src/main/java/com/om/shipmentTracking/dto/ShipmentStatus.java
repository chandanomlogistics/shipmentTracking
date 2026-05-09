package com.om.shipmentTracking.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentStatus {
    private String Status;
    private String StatusDateTime;
    private String StatusType;
    private String StatusLocation;
    private String StatusLatitude;
    private String StatusLongitude;
    private String ScanCode;
    private String Scan;
}

