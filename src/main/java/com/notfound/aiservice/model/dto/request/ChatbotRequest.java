package com.notfound.aiservice.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ChatbotRequest {

    @NotBlank
    private String message;

    private String sessionId;

    /**
     * UUID của user (nếu đã đăng nhập). Một số tool agent cần thông tin này
     * (recommendation cá nhân hoá, tra cứu đơn hàng, ...).
     */
    private String userId;

    private List<AttachmentRequest> attachments;
}
