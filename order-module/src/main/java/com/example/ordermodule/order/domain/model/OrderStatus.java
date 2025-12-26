package com.example.ordermodule.order.domain.model;

public enum OrderStatus {
    PENDING,            // 주문 생성 (결제 요청 전)
    PAYMENT_REQUESTED,  // 결제 요청됨
    PAYMENT_APPROVED,   // 결제 승인됨
    DELIVERY_REQUESTED, // 배송 요청됨
    CONFIRMED,          // 주문 확정 (배송 생성 완료)
    CANCELLED,          // 주문 취소됨
    FAILED              // 주문 실패
}
