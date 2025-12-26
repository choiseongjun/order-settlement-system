package com.example.order.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreatedDate;
import org.hibernate.annotations.UpdatedTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_saga_id", columnList = "sagaId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(length = 36)
    private String sagaId;

    @Column(length = 500)
    private String cancelReason;

    // 배송 정보 (간단하게)
    @Column(length = 200)
    private String deliveryAddress;

    @Column(length = 100)
    private String recipientName;

    @Column(length = 20)
    private String recipientPhone;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdatedTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Order(Long userId, Long productId, Integer quantity, BigDecimal totalAmount,
                 String deliveryAddress, String recipientName, String recipientPhone) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
        this.deliveryAddress = deliveryAddress;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 비즈니스 로직
    public void assignSaga(String sagaId) {
        this.sagaId = sagaId;
    }

    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
    }

    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }

    public boolean canBeCancelled() {
        return status == OrderStatus.PENDING ||
               status == OrderStatus.PAYMENT_REQUESTED ||
               status == OrderStatus.PAYMENT_APPROVED ||
               status == OrderStatus.DELIVERY_REQUESTED;
    }

    public boolean isCompleted() {
        return status == OrderStatus.CONFIRMED;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED || status == OrderStatus.FAILED;
    }
}
