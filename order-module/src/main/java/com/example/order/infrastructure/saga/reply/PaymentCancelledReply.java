package com.example.order.infrastructure.saga.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결제 취소 완료 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCancelledReply implements SagaReply {
    private String sagaId;
    private Long paymentId;

    @Override
    public String getReplyType() {
        return "PaymentCancelled";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
