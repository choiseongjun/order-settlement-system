package com.example.ordermodule.order.infrastructure.saga;

public enum SagaStatus {
    STARTED,        // Saga 시작
    COMPENSATING,   // 보상 트랜잭션 진행 중
    COMPLETED,      // Saga 성공 완료
    ABORTED         // Saga 실패 (보상 완료)
}
