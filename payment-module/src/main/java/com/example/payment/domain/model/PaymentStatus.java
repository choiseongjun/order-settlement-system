package com.example.payment.domain.model;

public enum PaymentStatus {
    PENDING,    // 결제 대기
    APPROVED,   // 결제 승인
    FAILED,     // 결제 실패
    CANCELLED   // 결제 취소
}
