package com.example.order.adapter.in.event;

import com.example.order.common.util.JsonUtil;
import com.example.order.infrastructure.idempotency.IdempotencyService;
import com.example.order.infrastructure.saga.OrderFulfillmentSagaOrchestrator;
import com.example.order.infrastructure.saga.reply.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Saga Reply Consumer - Orchestrator가 Participant로부터 응답을 수신
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyConsumer {

    private final OrderFulfillmentSagaOrchestrator sagaOrchestrator;
    private final IdempotencyService idempotencyService;

    /**
     * Payment Service 응답 수신
     */
    @KafkaListener(topics = "saga.reply.payment", groupId = "order-service")
    public void handlePaymentReply(String message) {
        log.info("Received payment reply: {}", message);

        // Reply 타입 파싱 (간단한 방법)
        if (message.contains("\"replyType\":\"PaymentApproved\"")) {
            handlePaymentApproved(message);
        } else if (message.contains("\"replyType\":\"PaymentFailed\"")) {
            handlePaymentFailed(message);
        } else if (message.contains("\"replyType\":\"PaymentCancelled\"")) {
            handlePaymentCancelled(message);
        }
    }

    private void handlePaymentApproved(String message) {
        PaymentApprovedReply reply = JsonUtil.fromJson(message, PaymentApprovedReply.class);

        String idempotencyKey = "PaymentApproved:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate payment approved reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handlePaymentApproved(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    private void handlePaymentFailed(String message) {
        PaymentFailedReply reply = JsonUtil.fromJson(message, PaymentFailedReply.class);

        String idempotencyKey = "PaymentFailed:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate payment failed reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handlePaymentFailed(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    private void handlePaymentCancelled(String message) {
        PaymentCancelledReply reply = JsonUtil.fromJson(message, PaymentCancelledReply.class);

        String idempotencyKey = "PaymentCancelled:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate payment cancelled reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handlePaymentCancelled(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    /**
     * Delivery Service 응답 수신
     */
    @KafkaListener(topics = "saga.reply.delivery", groupId = "order-service")
    public void handleDeliveryReply(String message) {
        log.info("Received delivery reply: {}", message);

        if (message.contains("\"replyType\":\"DeliveryCreated\"")) {
            handleDeliveryCreated(message);
        } else if (message.contains("\"replyType\":\"DeliveryFailed\"")) {
            handleDeliveryFailed(message);
        }
    }

    private void handleDeliveryCreated(String message) {
        DeliveryCreatedReply reply = JsonUtil.fromJson(message, DeliveryCreatedReply.class);

        String idempotencyKey = "DeliveryCreated:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate delivery created reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handleDeliveryCreated(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    private void handleDeliveryFailed(String message) {
        DeliveryFailedReply reply = JsonUtil.fromJson(message, DeliveryFailedReply.class);

        String idempotencyKey = "DeliveryFailed:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate delivery failed reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handleDeliveryFailed(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    /**
     * Settlement Service 응답 수신
     */
    @KafkaListener(topics = "saga.reply.settlement", groupId = "order-service")
    public void handleSettlementReply(String message) {
        log.info("Received settlement reply: {}", message);

        if (message.contains("\"replyType\":\"SettlementCreated\"")) {
            handleSettlementCreated(message);
        }
    }

    private void handleSettlementCreated(String message) {
        SettlementCreatedReply reply = JsonUtil.fromJson(message, SettlementCreatedReply.class);

        String idempotencyKey = "SettlementCreated:" + reply.getSagaId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate settlement created reply ignored: sagaId={}", reply.getSagaId());
            return;
        }

        sagaOrchestrator.handleSettlementCreated(reply);
        idempotencyService.markAsProcessed(idempotencyKey);
    }
}
