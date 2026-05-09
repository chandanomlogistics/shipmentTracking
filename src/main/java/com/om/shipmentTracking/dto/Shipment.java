package com.om.shipmentTracking.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shipment {
    private String SenderID;
    private String PickUpDate;
    private String Origin;
    private String Destination;
    private String ReferenceNo;
    private String AWB;
    private String AWBValidityDate;
    private String ShipmentMode;
    private String ExpectedDeliveryDate;
    private String Weight;
    private String ReceivedBy;
    private String Attempt_count;
    private String PodImage;
    private ShipmentStatus Status;
}
