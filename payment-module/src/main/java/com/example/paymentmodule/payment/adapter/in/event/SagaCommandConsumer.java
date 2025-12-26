package com.example.paymentmodule.payment.adapter.in.event;

import com.example.paymentmodule.payment.application.service.PaymentService;
import com.example.paymentmodule.payment.common.util.JsonUtil;
import com.example.paymentmodule.payment.domain.model.Payment;
import com.example.paymentmodule.payment.domain.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment Service Saga Command Consumer
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandConsumer {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "saga.command.payment.approve", groupId = "payment-service")
    public void handleApprovePaymentCommand(String message) {
        log.info("Received ApprovePaymentCommand: {}", message);

        try {
            Map<String, Object> command = JsonUtil.fromJson(message, Map.class);

            String sagaId = (String) command.get("sagaId");
            Long orderId = ((Number) command.get("orderId")).longValue();
            Long userId = ((Number) command.get("userId")).longValue();
            BigDecimal amount = new BigDecimal(command.get("amount").toString());
            String paymentMethod = (String) command.get("paymentMethod");

            // 결제 승인 처리
            Payment payment = paymentService.approvePayment(orderId, userId, amount, paymentMethod, sagaId);

            // 결과에 따라 Reply 발행
            if (payment.getStatus() == PaymentStatus.APPROVED) {
                sendPaymentApprovedReply(payment);
            } else {
                sendPaymentFailedReply(sagaId, payment.getFailureReason());
            }

        } catch (Exception e) {
            log.error("Failed to process ApprovePaymentCommand", e);
        }
    }

    @KafkaListener(topics = "saga.command.payment.cancel", groupId = "payment-service")
    public void handleCancelPaymentCommand(String message) {
        log.info("Received CancelPaymentCommand: {}", message);

        try {
            Map<String, Object> command = JsonUtil.fromJson(message, Map.class);

            String sagaId = (String) command.get("sagaId");
            Long paymentId = ((Number) command.get("paymentId")).longValue();

            // 결제 취소 처리
            paymentService.cancelPayment(paymentId);

            // Reply 발행
            sendPaymentCancelledReply(sagaId, paymentId);

        } catch (Exception e) {
            log.error("Failed to process CancelPaymentCommand", e);
        }
    }

    private void sendPaymentApprovedReply(Payment payment) {
        Map<String, Object> reply = Map.of(
            "sagaId", payment.getSagaId(),
            "paymentId", payment.getId(),
            "pgTransactionId", payment.getPgTransactionId(),
            "replyType", "PaymentApproved"
        );

        kafkaTemplate.send("saga.reply.payment", payment.getSagaId(), JsonUtil.toJson(reply));
        log.info("PaymentApprovedReply sent: sagaId={}, paymentId={}", payment.getSagaId(), payment.getId());
    }

    private void sendPaymentFailedReply(String sagaId, String reason) {
        Map<String, Object> reply = Map.of(
            "sagaId", sagaId,
            "reason", reason,
            "errorCode", "PG_FAILED",
            "replyType", "PaymentFailed"
        );

        kafkaTemplate.send("saga.reply.payment", sagaId, JsonUtil.toJson(reply));
        log.info("PaymentFailedReply sent: sagaId={}", sagaId);
    }

    private void sendPaymentCancelledReply(String sagaId, Long paymentId) {
        Map<String, Object> reply = Map.of(
            "sagaId", sagaId,
            "paymentId", paymentId,
            "replyType", "PaymentCancelled"
        );

        kafkaTemplate.send("saga.reply.payment", sagaId, JsonUtil.toJson(reply));
        log.info("PaymentCancelledReply sent: sagaId={}, paymentId={}", sagaId, paymentId);
    }
}
