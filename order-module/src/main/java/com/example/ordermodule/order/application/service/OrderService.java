package com.example.ordermodule.order.application.service;

import com.example.ordermodule.order.application.dto.OrderRequest;
import com.example.ordermodule.order.application.dto.OrderResponse;
import com.example.ordermodule.order.domain.model.Order;
import com.example.ordermodule.order.domain.repository.OrderRepository;
import com.example.ordermodule.order.infrastructure.saga.OrderFulfillmentSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderFulfillmentSagaOrchestrator sagaOrchestrator;

    /**
     * 주문 생성 및 Saga 시작
     */
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        // 1. 주문 엔티티 생성
        Order order = Order.builder()
            .userId(request.getUserId())
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .totalAmount(request.getTotalAmount())
            .deliveryAddress(request.getDeliveryAddress())
            .recipientName(request.getRecipientName())
            .recipientPhone(request.getRecipientPhone())
            .build();

        Order savedOrder = orderRepository.save(order);

        log.info("Order created: orderId={}, userId={}, totalAmount={}",
            savedOrder.getId(), savedOrder.getUserId(), savedOrder.getTotalAmount());

        // 2. Saga 시작 (결제 → 배송 → 정산)
        sagaOrchestrator.startSaga(savedOrder);

        return OrderResponse.from(savedOrder);
    }

    /**
     * 주문 조회
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        return OrderResponse.from(order);
    }

    /**
     * 사용자별 주문 목록 조회
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);

        return orders.stream()
            .map(OrderResponse::from)
            .collect(Collectors.toList());
    }

    /**
     * 모든 주문 조회
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<Order> orders = orderRepository.findAll();

        return orders.stream()
            .map(OrderResponse::from)
            .collect(Collectors.toList());
    }
}
