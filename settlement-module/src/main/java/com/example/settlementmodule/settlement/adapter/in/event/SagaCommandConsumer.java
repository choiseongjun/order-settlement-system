package com.example.settlementmodule.settlement.adapter.in.event;

import com.example.settlementmodule.settlement.application.service.SettlementService;
import com.example.settlementmodule.settlement.common.util.JsonUtil;
import com.example.settlementmodule.settlement.domain.model.SettlementTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandConsumer {

    private final SettlementService settlementService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "saga.command.settlement.create", groupId = "settlement-service")
    public void handleCreateSettlementCommand(String message) {
        log.info("Received CreateSettlementCommand: {}", message);

        try {
            Map<String, Object> command = JsonUtil.fromJson(message, Map.class);

            String sagaId = (String) command.get("sagaId");
            Long orderId = ((Number) command.get("orderId")).longValue();
            Long paymentId = ((Number) command.get("paymentId")).longValue();
            BigDecimal amount = new BigDecimal(command.get("amount").toString());

            // 정산 대상 추가
            SettlementTarget target = settlementService.addSettlementTarget(
                orderId, paymentId, amount, sagaId
            );

            // Reply 발행
            sendSettlementCreatedReply(target);

        } catch (Exception e) {
            log.error("Failed to process CreateSettlementCommand", e);
        }
    }

    private void sendSettlementCreatedReply(SettlementTarget target) {
        Map<String, Object> reply = Map.of(
            "sagaId", target.getSagaId(),
            "settlementId", target.getId(),
            "replyType", "SettlementCreated"
        );

        kafkaTemplate.send("saga.reply.settlement", target.getSagaId(), JsonUtil.toJson(reply));
        log.info("SettlementCreatedReply sent: sagaId={}, settlementId={}",
            target.getSagaId(), target.getId());
    }
}
