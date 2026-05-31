package com.notfound.aiservice.model.dto.request;

import lombok.Data;

@Data
public class LocationData {
    private Double lat;
    private Double lng;
    private String address;
}
