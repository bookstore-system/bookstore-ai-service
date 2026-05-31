package com.notfound.aiservice.model.dto.request;

import lombok.Data;

@Data
public class AttachmentRequest {
    private String type;
    private String url;
    private String name;
    private LocationData location;
}
