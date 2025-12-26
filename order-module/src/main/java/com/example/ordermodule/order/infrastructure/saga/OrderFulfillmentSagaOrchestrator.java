package com.example.ordermodule.order.infrastructure.saga;

import com.example.ordermodule.order.common.util.JsonUtil;
import com.example.ordermodule.order.domain.model.Order;
import com.example.ordermodule.order.domain.model.OrderStatus;
import com.example.ordermodule.order.domain.repository.OrderRepository;
import com.example.ordermodule.order.infrastructure.outbox.Outbox;
import com.example.ordermodule.order.infrastructure.outbox.OutboxRepository;
import com.example.ordermodule.order.infrastructure.saga.command.ApprovePaymentCommand;
import com.example.ordermodule.order.infrastructure.saga.command.CancelPaymentCommand;
import com.example.ordermodule.order.infrastructure.saga.command.CreateDeliveryCommand;
import com.example.ordermodule.order.infrastructure.saga.command.CreateSettlementCommand;
import com.example.ordermodule.order.infrastructure.saga.reply.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order Fulfillment Saga Orchestrator
 * Order → Payment → Delivery → Settlement 플로우 제어
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFulfillmentSagaOrchestrator {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    private static final String SAGA_TYPE = "ORDER_FULFILLMENT_SAGA";

    /**
     * Saga 시작: 주문 생성 후 결제 요청
     */
    @Transactional
    public SagaInstance startSaga(Order order) {
        // 1. Saga 인스턴스 생성
        SagaInstance saga = SagaInstance.builder()
            .sagaType(SAGA_TYPE)
            .aggregateId(order.getId())
            .payload(JsonUtil.toJson(order))
            .build();

        sagaInstanceRepository.save(saga);

        // 2. 주문에 Saga ID 연결
        order.assignSaga(saga.getSagaId());
        orderRepository.save(order);

        log.info("Saga started: sagaId={}, orderId={}", saga.getSagaId(), order.getId());

        // 3. 첫 번째 단계: 결제 승인 요청
        sendApprovePaymentCommand(saga, order);

        // 4. Saga 상태 업데이트
        saga.updateStep(SagaStep.PAYMENT_REQUESTED);
        sagaInstanceRepository.save(saga);

        return saga;
    }

    /**
     * 결제 승인 커맨드 발행 (Outbox 패턴)
     */
    private void sendApprovePaymentCommand(SagaInstance saga, Order order) {
        ApprovePaymentCommand command = ApprovePaymentCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .userId(order.getUserId())
            .amount(order.getTotalAmount())
            .paymentMethod("CARD")
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType("SAGA")
            .aggregateId(order.getId())
            .eventType("ApprovePaymentCommand")
            .topic("saga.command.payment.approve")
            .payload(JsonUtil.toJson(command))
            .build();

        outboxRepository.save(outbox);

        log.info("ApprovePaymentCommand saved to outbox: sagaId={}", saga.getSagaId());
    }

    /**
     * 결제 승인 성공 처리
     */
    @Transactional
    public void handlePaymentApproved(PaymentApprovedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        // 1. 보상 데이터에 결제 ID 저장
        saga.addCompensationData("paymentId", reply.getPaymentId());

        // 2. 주문 상태 업데이트
        Order order = orderRepository.findById(saga.getAggregateId())
            .orElseThrow(() -> new RuntimeException("Order not found: " + saga.getAggregateId()));
        order.updateStatus(OrderStatus.PAYMENT_APPROVED);
        orderRepository.save(order);

        // 3. Saga 다음 단계: 배송 생성 요청
        saga.updateStep(SagaStep.PAYMENT_APPROVED);
        sagaInstanceRepository.save(saga);

        sendCreateDeliveryCommand(saga, order);

        saga.updateStep(SagaStep.DELIVERY_REQUESTED);
        sagaInstanceRepository.save(saga);

        log.info("Payment approved, delivery requested: sagaId={}", saga.getSagaId());
    }

    /**
     * 결제 실패 처리 (보상 트랜잭션)
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        log.error("Payment failed: sagaId={}, reason={}", saga.getSagaId(), reply.getReason());

        // 1. Saga 보상 모드로 전환
        saga.updateStatus(SagaStatus.COMPENSATING);

        // 2. 주문 취소
        Order order = orderRepository.findById(saga.getAggregateId())
            .orElseThrow(() -> new RuntimeException("Order not found: " + saga.getAggregateId()));
        order.cancel("Payment failed: " + reply.getReason());
        orderRepository.save(order);

        // 3. Saga 중단 완료
        saga.updateStep(SagaStep.ORDER_CANCELLED, SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);

        log.info("Saga aborted due to payment failure: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 커맨드 발행 (Outbox 패턴)
     */
    private void sendCreateDeliveryCommand(SagaInstance saga, Order order) {
        CreateDeliveryCommand command = CreateDeliveryCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .userId(order.getUserId())
            .address(order.getDeliveryAddress())
            .recipientName(order.getRecipientName())
            .recipientPhone(order.getRecipientPhone())
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType("SAGA")
            .aggregateId(order.getId())
            .eventType("CreateDeliveryCommand")
            .topic("saga.command.delivery.create")
            .payload(JsonUtil.toJson(command))
            .build();

        outboxRepository.save(outbox);

        log.info("CreateDeliveryCommand saved to outbox: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 성공 처리
     */
    @Transactional
    public void handleDeliveryCreated(DeliveryCreatedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        // 1. 보상 데이터에 배송 ID 저장
        saga.addCompensationData("deliveryId", reply.getDeliveryId());

        // 2. 주문 상태 업데이트
        Order order = orderRepository.findById(saga.getAggregateId())
            .orElseThrow(() -> new RuntimeException("Order not found: " + saga.getAggregateId()));
        order.updateStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // 3. Saga 다음 단계: 정산 생성 요청
        saga.updateStep(SagaStep.DELIVERY_CREATED);
        sagaInstanceRepository.save(saga);

        sendCreateSettlementCommand(saga, order);

        saga.updateStep(SagaStep.SETTLEMENT_REQUESTED);
        sagaInstanceRepository.save(saga);

        log.info("Delivery created, settlement requested: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 실패 처리 (보상 트랜잭션)
     */
    @Transactional
    public void handleDeliveryFailed(DeliveryFailedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        log.error("Delivery failed: sagaId={}, reason={}", saga.getSagaId(), reply.getReason());

        // 1. Saga 보상 모드로 전환
        saga.updateStatus(SagaStatus.COMPENSATING);
        saga.updateStep(SagaStep.PAYMENT_CANCELLING);
        sagaInstanceRepository.save(saga);

        // 2. 결제 취소 커맨드 발행 (보상)
        Long paymentId = saga.getCompensationDataAs("paymentId", Long.class);
        sendCancelPaymentCommand(saga, paymentId, "Delivery failed");
    }

    /**
     * 결제 취소 커맨드 발행 (보상 - Outbox 패턴)
     */
    private void sendCancelPaymentCommand(SagaInstance saga, Long paymentId, String reason) {
        CancelPaymentCommand command = CancelPaymentCommand.builder()
            .sagaId(saga.getSagaId())
            .paymentId(paymentId)
            .reason(reason)
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType("SAGA")
            .aggregateId(saga.getAggregateId())
            .eventType("CancelPaymentCommand")
            .topic("saga.command.payment.cancel")
            .payload(JsonUtil.toJson(command))
            .build();

        outboxRepository.save(outbox);

        log.info("CancelPaymentCommand saved to outbox: sagaId={}, paymentId={}",
            saga.getSagaId(), paymentId);
    }

    /**
     * 결제 취소 완료 처리
     */
    @Transactional
    public void handlePaymentCancelled(PaymentCancelledReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        // 1. 주문 취소
        Order order = orderRepository.findById(saga.getAggregateId())
            .orElseThrow(() -> new RuntimeException("Order not found: " + saga.getAggregateId()));
        order.cancel("Delivery creation failed");
        orderRepository.save(order);

        // 2. Saga 중단 완료
        saga.updateStep(SagaStep.ORDER_CANCELLED, SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);

        log.info("Saga aborted, all compensations completed: sagaId={}", saga.getSagaId());
    }

    /**
     * 정산 생성 커맨드 발행 (Outbox 패턴)
     */
    private void sendCreateSettlementCommand(SagaInstance saga, Order order) {
        Long paymentId = saga.getCompensationDataAs("paymentId", Long.class);

        CreateSettlementCommand command = CreateSettlementCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .paymentId(paymentId)
            .amount(order.getTotalAmount())
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType("SAGA")
            .aggregateId(order.getId())
            .eventType("CreateSettlementCommand")
            .topic("saga.command.settlement.create")
            .payload(JsonUtil.toJson(command))
            .build();

        outboxRepository.save(outbox);

        log.info("CreateSettlementCommand saved to outbox: sagaId={}", saga.getSagaId());
    }

    /**
     * 정산 생성 성공 처리 (Saga 완료)
     */
    @Transactional
    public void handleSettlementCreated(SettlementCreatedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new RuntimeException("Saga not found: " + reply.getSagaId()));

        // Saga 완료
        saga.updateStep(SagaStep.SETTLEMENT_CREATED, SagaStatus.COMPLETED);
        sagaInstanceRepository.save(saga);

        log.info("Saga completed successfully: sagaId={}", saga.getSagaId());
    }
}
