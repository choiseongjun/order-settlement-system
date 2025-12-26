package com.example.ordermodule.order.infrastructure.saga.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배송 생성 성공 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryCreatedReply implements SagaReply {
    private String sagaId;
    private Long deliveryId;
    private String trackingNumber;

    @Override
    public String getReplyType() {
        return "DeliveryCreated";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
