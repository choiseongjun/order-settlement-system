package com.example.order.infrastructure.saga.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배송 생성 커맨드
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeliveryCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long userId;
    private String address;
    private String recipientName;
    private String recipientPhone;

    @Override
    public String getCommandType() {
        return "CreateDelivery";
    }
}
