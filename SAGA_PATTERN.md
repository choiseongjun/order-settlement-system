# Saga 패턴 구현 가이드

## 1. Saga 패턴이란?

마이크로서비스 환경에서 **분산 트랜잭션**을 관리하기 위한 패턴입니다. 각 서비스의 로컬 트랜잭션을 순차적으로 실행하고, 실패 시 **보상 트랜잭션(Compensation)**으로 롤백합니다.

### 1.1 문제 상황
```
주문 생성 → 결제 승인 → 재고 차감 → 배송 준비

만약 "재고 차감"에서 실패하면?
→ 주문 취소 + 결제 취소 필요 (보상 트랜잭션)
```

**2PC(Two-Phase Commit)의 한계**:
- 높은 지연 시간
- 가용성 저하 (Locking)
- 마이크로서비스에 적합하지 않음

**Saga 패턴의 해결책**:
- 각 단계를 독립적인 로컬 트랜잭션으로 실행
- 실패 시 이미 완료된 단계를 역순으로 보상
- **최종 일관성(Eventual Consistency)** 보장

## 2. Saga 패턴 유형

### 2.1 Choreography (코레오그래피) - 선택됨
**특징**:
- 각 서비스가 이벤트를 발행/구독
- 중앙 조정자 없음
- 느슨한 결합

**장점**:
- 서비스 자율성 높음
- 확장성 좋음
- 단순한 시나리오에 적합

**단점**:
- 전체 플로우 추적 어려움
- 순환 의존성 발생 가능
- 복잡한 비즈니스 로직 구현 어려움

### 2.2 Orchestration (오케스트레이션)
**특징**:
- 중앙 Orchestrator가 각 서비스에 커맨드 전송
- 명령형 프로그래밍

**장점**:
- 전체 플로우 관리 용이
- 명확한 책임 분리
- 복잡한 비즈니스 로직 구현 가능

**단점**:
- Orchestrator가 SPOF 가능성
- 서비스 간 결합도 증가
- Orchestrator 복잡도 증가

## 3. 프로젝트 적용: Choreography Saga

본 프로젝트는 **Choreography 방식**을 채택합니다.

**이유**:
- 3개 서비스로 복잡도가 낮음
- 각 서비스의 자율성 극대화
- Kafka 기반 이벤트 아키텍처와 자연스럽게 통합

## 4. 주문-결제 Saga 플로우

### 4.1 정상 플로우 (Happy Path)

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│ Order Service│       │Payment Service│      │Settlement Svc│
└──────┬───────┘       └──────┬───────┘       └──────┬───────┘
       │                      │                      │
  1. Create Order             │                      │
  (status: PENDING)           │                      │
       │                      │                      │
  2. Save to Outbox           │                      │
  (OrderCreated event)        │                      │
       │                      │                      │
  ─────┼──────────────────────┼──────────────────────┤
  Kafka│ order.created        │                      │
  ─────┼──────────────────────>                      │
       │                3. Consume event             │
       │                   (OrderCreated)            │
       │                      │                      │
       │                4. Approve payment           │
       │                   (PG 연동)                 │
       │                      │                      │
       │                5. Save to Outbox            │
       │                (PaymentSucceeded)           │
       │                      │                      │
  ─────┼──────────────────────┼──────────────────────┤
  Kafka│ payment.succeeded    │                      │
  <────┼──────────────────────┤                      │
       │                      │────────────────────> │
  6. Consume event            │                7. Consume event
  (PaymentSucceeded)          │                (PaymentSucceeded)
       │                      │                      │
  7. Update order             │                8. Create settlement
  (status: CONFIRMED)         │                   record
       │                      │                      │
  8. Save to Outbox           │                      │
  (OrderConfirmed)            │                      │
       ▼                      ▼                      ▼
     [END]                  [END]                  [END]
```

### 4.2 보상 플로우 (결제 실패 시)

```
┌──────────────┐       ┌──────────────┐
│ Order Service│       │Payment Service│
└──────┬───────┘       └──────┬───────┘
       │                      │
  1. Create Order             │
  (status: PENDING)           │
       │                      │
  ─────┼──────────────────────┤
  Kafka│ order.created        │
  ─────┼──────────────────────>
       │                2. Consume event
       │                      │
       │                3. Approve payment
       │                   (PG 실패! ❌)
       │                      │
       │                4. Save to Outbox
       │                (PaymentFailed)
       │                      │
  ─────┼──────────────────────┤
  Kafka│ payment.failed       │
  <────┼──────────────────────┤
       │                      │
  5. Consume event            │
  (PaymentFailed)             │
       │                      │
  6. Cancel order             │
  (status: CANCELLED)         │
       │                      │
  7. Save to Outbox           │
  (OrderCancelled)            │
       ▼                      ▼
     [END]                  [END]
```

## 5. Saga 상태 관리

### 5.1 Saga Instance 테이블
각 서비스는 Saga의 진행 상태를 로컬 DB에 저장합니다.

```sql
CREATE TABLE saga_instance (
    id                VARCHAR(36) PRIMARY KEY,        -- UUID
    saga_type         VARCHAR(50) NOT NULL,           -- ORDER_PAYMENT_SAGA
    aggregate_id      BIGINT NOT NULL,                -- 주문ID
    current_step      VARCHAR(50) NOT NULL,           -- PAYMENT_REQUESTED, PAYMENT_COMPLETED 등
    status            VARCHAR(20) NOT NULL,           -- STARTED, COMPENSATING, COMPLETED, ABORTED
    payload           JSONB NOT NULL,                 -- Saga 컨텍스트 (필요한 모든 데이터)
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),

    INDEX idx_aggregate (aggregate_id),
    INDEX idx_status (status)
);
```

### 5.2 JPA Entity
```java
@Entity
@Table(name = "saga_instance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaInstance {

    @Id
    private String id;  // UUID

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SagaType sagaType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateStep(SagaStep step, SagaStatus status) {
        this.currentStep = step;
        this.status = status;
    }
}

enum SagaType {
    ORDER_PAYMENT_SAGA
}

enum SagaStep {
    // Order Service
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,

    // Payment Service
    PAYMENT_PENDING,
    PAYMENT_APPROVED,
    PAYMENT_FAILED,
    PAYMENT_COMPENSATED
}

enum SagaStatus {
    STARTED,        // Saga 시작
    COMPENSATING,   // 보상 트랜잭션 진행 중
    COMPLETED,      // Saga 성공 완료
    ABORTED         // Saga 실패 (보상 완료)
}
```

## 6. 구현 예시

### 6.1 Order Service: 주문 생성 (Saga 시작)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final OutboxRepository outboxRepository;

    public OrderResponse createOrder(OrderRequest request) {
        // 1. Saga 시작
        SagaInstance saga = SagaInstance.builder()
            .sagaType(SagaType.ORDER_PAYMENT_SAGA)
            .currentStep(SagaStep.ORDER_CREATED)
            .status(SagaStatus.STARTED)
            .payload(toJson(request))
            .build();

        sagaInstanceRepository.save(saga);

        // 2. 주문 생성
        Order order = Order.builder()
            .userId(request.getUserId())
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .totalAmount(request.getTotalAmount())
            .status(OrderStatus.PENDING)
            .sagaId(saga.getId())  // Saga ID 연결
            .build();

        Order savedOrder = orderRepository.save(order);

        // 3. Saga 상태 업데이트
        saga.setAggregateId(savedOrder.getId());
        saga.updateStep(SagaStep.PAYMENT_REQUESTED, SagaStatus.STARTED);

        // 4. Outbox에 이벤트 발행
        publishOrderCreatedEvent(savedOrder, saga.getId());

        return OrderResponse.from(savedOrder);
    }

    private void publishOrderCreatedEvent(Order order, String sagaId) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .sagaId(sagaId)
            .orderId(order.getId())
            .userId(order.getUserId())
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .totalAmount(order.getTotalAmount())
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType(AggregateType.ORDER)
            .aggregateId(order.getId())
            .eventType("OrderCreated")
            .payload(toJson(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(outbox);
    }
}
```

### 6.2 Payment Service: 결제 승인 (Saga 진행)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentEventHandler {

    private final PaymentService paymentService;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 1. 멱등성 체크
        String idempotencyKey = "OrderCreated:" + event.getOrderId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            log.info("Duplicate event ignored: {}", idempotencyKey);
            return;
        }

        // 2. Saga 인스턴스 생성
        SagaInstance saga = SagaInstance.builder()
            .id(event.getSagaId())
            .sagaType(SagaType.ORDER_PAYMENT_SAGA)
            .aggregateId(event.getOrderId())
            .currentStep(SagaStep.PAYMENT_PENDING)
            .status(SagaStatus.STARTED)
            .payload(toJson(event))
            .build();

        sagaInstanceRepository.save(saga);

        try {
            // 3. 결제 승인 시도
            Payment payment = paymentService.approvePayment(
                event.getOrderId(),
                event.getUserId(),
                event.getTotalAmount()
            );

            // 4. Saga 상태 업데이트
            saga.updateStep(SagaStep.PAYMENT_APPROVED, SagaStatus.STARTED);
            sagaInstanceRepository.save(saga);

            // 5. 성공 이벤트 발행
            publishPaymentSucceededEvent(payment, event.getSagaId());

            // 6. 멱등성 키 저장
            idempotencyService.markAsProcessed(idempotencyKey);

        } catch (PaymentException e) {
            // 7. 결제 실패 시
            log.error("Payment failed for order: {}", event.getOrderId(), e);

            saga.updateStep(SagaStep.PAYMENT_FAILED, SagaStatus.COMPENSATING);
            sagaInstanceRepository.save(saga);

            // 8. 실패 이벤트 발행 (보상 트랜잭션 시작)
            publishPaymentFailedEvent(event.getOrderId(), event.getSagaId(), e.getMessage());

            // 9. 멱등성 키 저장 (실패도 처리됨으로 간주)
            idempotencyService.markAsProcessed(idempotencyKey);
        }
    }

    private void publishPaymentSucceededEvent(Payment payment, String sagaId) {
        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
            .sagaId(sagaId)
            .paymentId(payment.getId())
            .orderId(payment.getOrderId())
            .amount(payment.getAmount())
            .pgTransactionId(payment.getPgTransactionId())
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType(AggregateType.PAYMENT)
            .aggregateId(payment.getId())
            .eventType("PaymentSucceeded")
            .payload(toJson(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(outbox);
    }

    private void publishPaymentFailedEvent(Long orderId, String sagaId, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
            .sagaId(sagaId)
            .orderId(orderId)
            .reason(reason)
            .build();

        Outbox outbox = Outbox.builder()
            .aggregateType(AggregateType.PAYMENT)
            .aggregateId(orderId)
            .eventType("PaymentFailed")
            .payload(toJson(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(outbox);
    }
}
```

### 6.3 Order Service: 결제 성공 처리 (Saga 완료)

```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderEventHandler {

    private final OrderRepository orderRepository;
    private final SagaInstanceRepository sagaInstanceRepository;
    private final OutboxRepository outboxRepository;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "payment.succeeded", groupId = "order-service")
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        // 1. 멱등성 체크
        String idempotencyKey = "PaymentSucceeded:" + event.getOrderId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            return;
        }

        // 2. 주문 조회
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        // 3. 주문 상태 업데이트
        order.confirm(event.getPaymentId());
        orderRepository.save(order);

        // 4. Saga 상태 업데이트 (완료)
        SagaInstance saga = sagaInstanceRepository.findById(event.getSagaId())
            .orElseThrow(() -> new SagaNotFoundException(event.getSagaId()));

        saga.updateStep(SagaStep.ORDER_CONFIRMED, SagaStatus.COMPLETED);
        sagaInstanceRepository.save(saga);

        // 5. OrderConfirmed 이벤트 발행
        publishOrderConfirmedEvent(order, event.getSagaId());

        // 6. 멱등성 키 저장
        idempotencyService.markAsProcessed(idempotencyKey);
    }

    @KafkaListener(topics = "payment.failed", groupId = "order-service")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // 1. 멱등성 체크
        String idempotencyKey = "PaymentFailed:" + event.getOrderId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            return;
        }

        // 2. 주문 조회
        Order order = orderRepository.findById(event.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        // 3. 보상 트랜잭션: 주문 취소
        order.cancel(event.getReason());
        orderRepository.save(order);

        // 4. Saga 상태 업데이트 (중단됨)
        SagaInstance saga = sagaInstanceRepository.findById(event.getSagaId())
            .orElseThrow(() -> new SagaNotFoundException(event.getSagaId()));

        saga.updateStep(SagaStep.ORDER_CANCELLED, SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);

        // 5. OrderCancelled 이벤트 발행 (다른 서비스에 알림)
        publishOrderCancelledEvent(order, event.getSagaId());

        // 6. 멱등성 키 저장
        idempotencyService.markAsProcessed(idempotencyKey);
    }
}
```

### 6.4 Settlement Service: 정산 처리

```java
@Service
@RequiredArgsConstructor
@Transactional
public class SettlementEventHandler {

    private final SettlementService settlementService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "payment.succeeded", groupId = "settlement-service")
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        // 1. 멱등성 체크
        String idempotencyKey = "PaymentSucceeded:" + event.getPaymentId();
        if (idempotencyService.isProcessed(idempotencyKey)) {
            return;
        }

        // 2. 정산 대상 추가
        settlementService.addSettlementTarget(
            event.getOrderId(),
            event.getPaymentId(),
            event.getAmount()
        );

        // 3. 멱등성 키 저장
        idempotencyService.markAsProcessed(idempotencyKey);

        log.info("Settlement target added: orderId={}, paymentId={}",
            event.getOrderId(), event.getPaymentId());
    }
}
```

## 7. 멱등성 보장 (Idempotency)

### 7.1 Idempotency Service (Redis 기반)

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long TTL_DAYS = 7;

    public boolean isProcessed(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void markAsProcessed(String key) {
        redisTemplate.opsForValue().set(
            key,
            LocalDateTime.now().toString(),
            Duration.ofDays(TTL_DAYS)
        );
    }

    public void remove(String key) {
        redisTemplate.delete(key);
    }
}
```

### 7.2 Idempotency Key 전략

**이벤트 기반 Key**:
```
{EventType}:{AggregateId}

예시:
- OrderCreated:12345
- PaymentSucceeded:67890
- PaymentFailed:12345
```

**장점**:
- 간단하고 명확
- 같은 이벤트의 중복 처리 방지

**주의사항**:
- TTL 설정 (7일 후 자동 삭제)
- Redis 장애 시 대비 (Fallback to DB)

## 8. Saga 모니터링

### 8.1 Saga Dashboard

```java
@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
public class SagaMonitorController {

    private final SagaInstanceRepository sagaInstanceRepository;

    @GetMapping
    public Page<SagaInstanceDto> getAllSagas(
        @RequestParam(required = false) SagaStatus status,
        Pageable pageable
    ) {
        if (status != null) {
            return sagaInstanceRepository.findByStatus(status, pageable)
                .map(SagaInstanceDto::from);
        }
        return sagaInstanceRepository.findAll(pageable)
            .map(SagaInstanceDto::from);
    }

    @GetMapping("/{sagaId}")
    public SagaInstanceDto getSagaDetails(@PathVariable String sagaId) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId)
            .orElseThrow(() -> new SagaNotFoundException(sagaId));

        return SagaInstanceDto.from(saga);
    }

    @GetMapping("/stats")
    public SagaStats getStats() {
        return SagaStats.builder()
            .totalCount(sagaInstanceRepository.count())
            .completedCount(sagaInstanceRepository.countByStatus(SagaStatus.COMPLETED))
            .abortedCount(sagaInstanceRepository.countByStatus(SagaStatus.ABORTED))
            .compensatingCount(sagaInstanceRepository.countByStatus(SagaStatus.COMPENSATING))
            .build();
    }
}
```

### 8.2 Saga Metrics (Prometheus)

```java
@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60000) // 1분마다
    public void recordMetrics() {
        meterRegistry.gauge("saga.total", sagaInstanceRepository.count());
        meterRegistry.gauge("saga.completed",
            sagaInstanceRepository.countByStatus(SagaStatus.COMPLETED));
        meterRegistry.gauge("saga.aborted",
            sagaInstanceRepository.countByStatus(SagaStatus.ABORTED));
        meterRegistry.gauge("saga.compensating",
            sagaInstanceRepository.countByStatus(SagaStatus.COMPENSATING));
    }

    public void recordSagaCompletion(SagaType sagaType, boolean success) {
        Counter.builder("saga.completion")
            .tag("type", sagaType.name())
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .increment();
    }
}
```

## 9. 장애 시나리오 및 대응

### 9.1 시나리오 1: Payment Service 장애

**상황**: 결제 서비스가 다운되어 OrderCreated 이벤트 처리 불가

**대응**:
1. Kafka Consumer가 메시지를 계속 보관 (Consumer Lag 증가)
2. Payment Service 복구 후 밀린 메시지 자동 처리
3. 주문은 PENDING 상태로 유지 (타임아웃 설정 필요)

**개선**:
```java
// Order Service: 타임아웃 처리
@Scheduled(fixedDelay = 300000) // 5분마다
public void handlePendingOrders() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);

    List<Order> timedOutOrders = orderRepository
        .findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

    for (Order order : timedOutOrders) {
        log.warn("Order timed out: {}", order.getId());
        order.cancel("Payment timeout");
        orderRepository.save(order);

        // Saga 상태 업데이트
        SagaInstance saga = sagaInstanceRepository.findById(order.getSagaId())
            .orElseThrow();
        saga.updateStep(SagaStep.ORDER_CANCELLED, SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);
    }
}
```

### 9.2 시나리오 2: Kafka Broker 장애

**상황**: Kafka가 일시적으로 다운

**대응**:
1. Outbox 메시지가 PENDING 상태로 누적
2. Kafka 복구 후 Outbox Relay가 자동으로 발행
3. 메시지 유실 없음 (Outbox 패턴의 장점)

### 9.3 시나리오 3: 보상 트랜잭션 실패

**상황**: 결제 취소 API 호출 실패

**대응**:
```java
@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final PaymentRepository paymentRepository;

    @KafkaListener(topics = "order.cancelled", groupId = "payment-service")
    @Retryable(
        value = { PaymentException.class },
        maxAttempts = 5,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void handleOrderCancelled(OrderCancelledEvent event) {
        Payment payment = paymentRepository.findByOrderId(event.getOrderId())
            .orElseThrow();

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            // 보상 트랜잭션: 결제 취소
            paymentService.cancelPayment(payment.getId());
        }
    }

    @Recover
    public void recoverCancellation(PaymentException e, OrderCancelledEvent event) {
        log.error("Payment cancellation failed after retries: {}", event.getOrderId());
        // DLQ로 이동 또는 수동 처리 알람
        sendManualInterventionAlert(event);
    }
}
```

## 10. Saga 패턴 모범 사례

### 10.1 DO
- ✅ 각 Saga 단계는 멱등하게 설계
- ✅ Saga 상태를 로컬 DB에 저장
- ✅ 타임아웃 처리 구현
- ✅ 보상 트랜잭션은 실패하지 않도록 설계 (재시도 필수)
- ✅ Saga ID를 모든 이벤트에 포함
- ✅ 분산 추적 (Trace ID) 활용

### 10.2 DON'T
- ❌ 보상 트랜잭션이 실패하는 경우를 간과
- ❌ Saga 상태를 공유 스토리지에 저장 (결합도 증가)
- ❌ 너무 많은 단계를 하나의 Saga에 포함 (3-5단계 권장)
- ❌ 동기 호출 남발 (비동기 이벤트 기반 유지)

## 11. 요약

| 항목 | 내용 |
|------|------|
| 패턴 유형 | Choreography (코레오그래피) |
| 목적 | 분산 트랜잭션 관리 |
| 핵심 아이디어 | 로컬 트랜잭션 + 보상 트랜잭션 |
| 장점 | 높은 가용성, 확장성, 느슨한 결합 |
| 단점 | 복잡도 증가, 디버깅 어려움 |
| 적용 대상 | 주문-결제 플로우 |
| 보상 전략 | 역순 보상 (결제 실패 → 주문 취소) |
| 멱등성 | Redis 기반 Idempotency Key |
| 모니터링 | Saga Instance 테이블 + Prometheus |
