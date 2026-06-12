package com.coconut.coconut_marketplace.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemDto {
    private Long productId;
    private String productName;
    private String imageUrl;
    private String categoryDisplayName;
    private BigDecimal price;
    private Integer quantity;
    private String storeName;
    private Integer stockQuantity;

    public BigDecimal getTotalPrice() {
        if (price == null || quantity == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
