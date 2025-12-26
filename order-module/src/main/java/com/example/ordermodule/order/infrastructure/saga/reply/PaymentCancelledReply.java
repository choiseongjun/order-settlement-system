package com.example.ordermodule.order.infrastructure.saga.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
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
