package com.example.order.infrastructure.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "saga_instance", indexes = {
    @Index(name = "idx_saga_aggregate_id", columnList = "aggregateId"),
    @Index(name = "idx_saga_status", columnList = "status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance {

    @Id
    @Column(length = 36)
    private String sagaId;

    @Column(nullable = false, length = 50)
    private String sagaType;

    @Column(nullable = false)
    private Long aggregateId; // Order ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SagaStep currentStep;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // Saga 컨텍스트 (주문 정보)

    @Column(columnDefinition = "TEXT")
    private String compensationData; // 보상에 필요한 데이터 (결제ID, 배송ID 등)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public SagaInstance(String sagaType, Long aggregateId, String payload) {
        this.sagaId = UUID.randomUUID().toString();
        this.sagaType = sagaType;
        this.aggregateId = aggregateId;
        this.status = SagaStatus.STARTED;
        this.currentStep = SagaStep.ORDER_CREATED;
        this.payload = payload;
        this.compensationData = "{}";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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

    public void updateStep(SagaStep step) {
        this.currentStep = step;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStatus(SagaStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateStep(SagaStep step, SagaStatus status) {
        this.currentStep = step;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    @SuppressWarnings("unchecked")
    public void addCompensationData(String key, Object value) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(this.compensationData, HashMap.class);
            data.put(key, value);
            this.compensationData = mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to update compensation data", e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getCompensationDataAs(String key, Class<T> type) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> data = mapper.readValue(this.compensationData, HashMap.class);
            Object value = data.get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return type.cast(value);
            }
            // Handle Number to Long conversion
            if (type == Long.class && value instanceof Number) {
                return type.cast(((Number) value).longValue());
            }
            return mapper.convertValue(value, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to read compensation data", e);
        }
    }
}
