package com.example.delivery.adapter.in.event;

import com.example.delivery.application.service.DeliveryService;
import com.example.delivery.common.util.JsonUtil;
import com.example.delivery.domain.model.Delivery;
import com.example.delivery.domain.model.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandConsumer {

    private final DeliveryService deliveryService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "saga.command.delivery.create", groupId = "delivery-service")
    public void handleCreateDeliveryCommand(String message) {
        log.info("Received CreateDeliveryCommand: {}", message);

        try {
            Map<String, Object> command = JsonUtil.fromJson(message, Map.class);

            String sagaId = (String) command.get("sagaId");
            Long orderId = ((Number) command.get("orderId")).longValue();
            Long userId = ((Number) command.get("userId")).longValue();
            String address = (String) command.get("address");
            String recipientName = (String) command.get("recipientName");
            String recipientPhone = (String) command.get("recipientPhone");

            // 배송 생성 처리
            Delivery delivery = deliveryService.createDelivery(
                orderId, userId, address, recipientName, recipientPhone, sagaId
            );

            // 결과에 따라 Reply 발행
            if (delivery.getStatus() == DeliveryStatus.CREATED) {
                sendDeliveryCreatedReply(delivery);
            } else {
                sendDeliveryFailedReply(sagaId, delivery.getFailureReason());
            }

        } catch (Exception e) {
            log.error("Failed to process CreateDeliveryCommand", e);
        }
    }

    @KafkaListener(topics = "saga.command.delivery.cancel", groupId = "delivery-service")
    public void handleCancelDeliveryCommand(String message) {
        log.info("Received CancelDeliveryCommand: {}", message);

        try {
            Map<String, Object> command = JsonUtil.fromJson(message, Map.class);

            String sagaId = (String) command.get("sagaId");
            Long deliveryId = ((Number) command.get("deliveryId")).longValue();

            // 배송 취소 처리
            deliveryService.cancelDelivery(deliveryId);

            // Reply 발행
            sendDeliveryCancelledReply(sagaId, deliveryId);

        } catch (Exception e) {
            log.error("Failed to process CancelDeliveryCommand", e);
        }
    }

    private void sendDeliveryCreatedReply(Delivery delivery) {
        Map<String, Object> reply = Map.of(
            "sagaId", delivery.getSagaId(),
            "deliveryId", delivery.getId(),
            "trackingNumber", delivery.getTrackingNumber(),
            "replyType", "DeliveryCreated"
        );

        kafkaTemplate.send("saga.reply.delivery", delivery.getSagaId(), JsonUtil.toJson(reply));
        log.info("DeliveryCreatedReply sent: sagaId={}, deliveryId={}",
            delivery.getSagaId(), delivery.getId());
    }

    private void sendDeliveryFailedReply(String sagaId, String reason) {
        Map<String, Object> reply = Map.of(
            "sagaId", sagaId,
            "reason", reason,
            "replyType", "DeliveryFailed"
        );

        kafkaTemplate.send("saga.reply.delivery", sagaId, JsonUtil.toJson(reply));
        log.info("DeliveryFailedReply sent: sagaId={}", sagaId);
    }

    private void sendDeliveryCancelledReply(String sagaId, Long deliveryId) {
        Map<String, Object> reply = Map.of(
            "sagaId", sagaId,
            "deliveryId", deliveryId,
            "replyType", "DeliveryCancelled"
        );

        kafkaTemplate.send("saga.reply.delivery", sagaId, JsonUtil.toJson(reply));
        log.info("DeliveryCancelledReply sent: sagaId={}, deliveryId={}", sagaId, deliveryId);
    }
}
