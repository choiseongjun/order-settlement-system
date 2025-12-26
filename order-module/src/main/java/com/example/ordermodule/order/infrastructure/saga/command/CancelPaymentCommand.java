package com.example.ordermodule.order.infrastructure.saga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 결제 취소 커맨드 (보상 트랜잭션)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentCommand implements SagaCommand {
    private String sagaId;
    private Long paymentId;
    private String reason;

    @Override
    public String getCommandType() {
        return "CancelPayment";
    }
}
