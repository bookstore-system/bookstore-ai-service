package com.notfound.aiservice.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Card sách chuẩn hoá để FE render. Mọi tool agent (search, filter,
 * recommendation, compare, image-scanner, ...) sau khi chạy đều được
 * gom dữ liệu sách về cùng shape này — FE chỉ cần đọc {@code response.books}
 * thay vì phải parse từng kiểu {@code data} riêng cho từng tool.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookCard {
    private String id;
    private String title;
    private Double price;
    private Double discountPrice;
    private String mainImageUrl;
    private Double averageRating;
    private Integer reviewCount;
    private Integer stockQuantity;
    private List<String> authorNames;

    /** Tên tool đã sinh ra card này (giúp FE/devtool debug). */
    private String source;
}
