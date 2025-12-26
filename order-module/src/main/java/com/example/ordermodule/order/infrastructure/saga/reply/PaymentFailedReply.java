package com.example.ordermodule.order.infrastructure.saga.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결제 실패 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedReply implements SagaReply {
    private String sagaId;
    private String reason;
    private String errorCode;

    @Override
    public String getReplyType() {
        return "PaymentFailed";
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}
