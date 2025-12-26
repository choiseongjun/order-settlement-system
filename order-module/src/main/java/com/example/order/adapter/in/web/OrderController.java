package com.example.order.adapter.in.web;

import com.example.order.application.dto.OrderRequest;
import com.example.order.application.dto.OrderResponse;
import com.example.order.application.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 생성
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("POST /api/orders - userId={}, productId={}, amount={}",
            request.getUserId(), request.getProductId(), request.getTotalAmount());

        OrderResponse response = orderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 주문 조회
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long orderId) {
        log.info("GET /api/orders/{}", orderId);

        OrderResponse response = orderService.getOrder(orderId);

        return ResponseEntity.ok(response);
    }

    /**
     * 사용자별 주문 목록 조회
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponse>> getOrdersByUser(@PathVariable Long userId) {
        log.info("GET /api/orders/user/{}", userId);

        List<OrderResponse> responses = orderService.getOrdersByUserId(userId);

        return ResponseEntity.ok(responses);
    }

    /**
     * 모든 주문 조회
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        log.info("GET /api/orders");

        List<OrderResponse> responses = orderService.getAllOrders();

        return ResponseEntity.ok(responses);
    }
}
