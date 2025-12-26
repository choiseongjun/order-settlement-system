package com.example.ordermodule.order.infrastructure.saga.reply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 배송 생성 실패 응답
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliveryFailedReply implements SagaReply {
    private String sagaId;
    private String reason;

    @Override
    public String getReplyType() {
        return "DeliveryFailed";
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}
