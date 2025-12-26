package com.example.delivery.domain.model;

public enum DeliveryStatus {
    PENDING,    // 배송 대기
    CREATED,    // 배송 생성
    IN_TRANSIT, // 배송 중
    DELIVERED,  // 배송 완료
    CANCELLED   // 배송 취소
}
