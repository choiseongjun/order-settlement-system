package com.example.settlementmodule.settlement.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_targets", indexes = {
    @Index(name = "idx_settlement_order_id", columnList = "orderId"),
    @Index(name = "idx_settlement_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    private BigDecimal fee;  // 수수료

    @Column(precision = 19, scale = 2)
    private BigDecimal netAmount;  // 정산 금액 (amount - fee)

    @Column(length = 36)
    private String sagaId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SettlementTarget(Long orderId, Long paymentId, BigDecimal amount, String sagaId) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.sagaId = sagaId;
        // 수수료 3% 계산
        this.fee = amount.multiply(new BigDecimal("0.03"));
        this.netAmount = amount.subtract(this.fee);
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
