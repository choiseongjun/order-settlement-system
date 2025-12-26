package com.example.ordermodule.order.infrastructure.saga.reply;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 정산 생성 성공 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementCreatedReply implements SagaReply {
    private String sagaId;
    private Long settlementId;

    @Override
    public String getReplyType() {
        return "SettlementCreated";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
