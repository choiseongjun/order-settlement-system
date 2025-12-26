package com.example.ordermodule.order.infrastructure.saga.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결제 승인 성공 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentApprovedReply implements SagaReply {
    private String sagaId;
    private Long paymentId;
    private String pgTransactionId;

    @Override
    public String getReplyType() {
        return "PaymentApproved";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
