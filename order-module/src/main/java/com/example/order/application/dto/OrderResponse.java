package com.example.order.application.dto;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String sagaId;
    private String deliveryAddress;
    private String recipientName;
    private String recipientPhone;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
            .id(order.getId())
            .userId(order.getUserId())
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .totalAmount(order.getTotalAmount())
            .status(order.getStatus())
            .sagaId(order.getSagaId())
            .deliveryAddress(order.getDeliveryAddress())
            .recipientName(order.getRecipientName())
            .recipientPhone(order.getRecipientPhone())
            .createdAt(order.getCreatedAt())
            .updatedAt(order.getUpdatedAt())
            .build();
    }
}
