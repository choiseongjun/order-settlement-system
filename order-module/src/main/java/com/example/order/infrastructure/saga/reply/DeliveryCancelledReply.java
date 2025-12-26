package com.example.order.infrastructure.saga.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배송 취소 완료 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryCancelledReply implements SagaReply {
    private String sagaId;
    private Long deliveryId;

    @Override
    public String getReplyType() {
        return "DeliveryCancelled";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
