package com.example.payment.application.service;

import com.example.payment.domain.model.Payment;
import com.example.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payment Service - 결제 승인/취소 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제 승인 처리 (Mock PG)
     */
    @Transactional
    public Payment approvePayment(Long orderId, Long userId, BigDecimal amount, String paymentMethod, String sagaId) {
        // Mock PG 승인 로직 (80% 성공률)
        boolean isSuccess = Math.random() < 0.8;

        Payment payment = Payment.builder()
            .orderId(orderId)
            .userId(userId)
            .amount(amount)
            .paymentMethod(paymentMethod)
            .sagaId(sagaId)
            .build();

        if (isSuccess) {
            String pgTransactionId = "PG-" + UUID.randomUUID().toString();
            payment.approve(pgTransactionId);
            log.info("Payment approved: orderId={}, pgTxId={}", orderId, pgTransactionId);
        } else {
            payment.fail("PG approval failed (Mock)");
            log.warn("Payment failed: orderId={}", orderId);
        }

        return paymentRepository.save(payment);
    }

    /**
     * 결제 취소 처리 (보상 트랜잭션)
     */
    @Transactional
    public void cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        payment.cancel();
        paymentRepository.save(payment);

        log.info("Payment cancelled: paymentId={}, orderId={}", paymentId, payment.getOrderId());
    }
}
