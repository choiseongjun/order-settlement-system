package com.example.order.infrastructure.saga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 결제 승인 커맨드
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePaymentCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String paymentMethod;

    @Override
    public String getCommandType() {
        return "ApprovePayment";
    }
}
