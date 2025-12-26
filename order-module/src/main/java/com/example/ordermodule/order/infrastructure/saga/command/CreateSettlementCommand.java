package com.example.ordermodule.order.infrastructure.saga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 정산 생성 커맨드
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSettlementCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long paymentId;
    private BigDecimal amount;

    @Override
    public String getCommandType() {
        return "CreateSettlement";
    }
}
