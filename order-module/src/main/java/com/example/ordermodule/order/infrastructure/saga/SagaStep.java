package com.example.order.infrastructure.saga;

public enum SagaStep {
    // Forward steps
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    PAYMENT_APPROVED,
    DELIVERY_REQUESTED,
    DELIVERY_CREATED,
    SETTLEMENT_REQUESTED,
    SETTLEMENT_CREATED,

    // Compensation steps
    PAYMENT_CANCELLING,
    PAYMENT_CANCELLED,
    DELIVERY_CANCELLING,
    DELIVERY_CANCELLED,
    ORDER_CANCELLED
}
