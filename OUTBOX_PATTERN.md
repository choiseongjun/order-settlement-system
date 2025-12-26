# Transactional Outbox 패턴 구현 가이드

## 1. Outbox 패턴이란?

마이크로서비스에서 **데이터베이스 트랜잭션**과 **메시지 발행**을 원자적으로 처리하기 위한 패턴입니다.

### 1.1 문제 상황
```java
// ❌ 문제: 트랜잭션과 메시지 발행이 원자적이지 않음
@Transactional
public void createOrder(OrderRequest request) {
    Order order = orderRepository.save(new Order(request));

    // 만약 이 시점에 애플리케이션이 죽으면?
    // → DB에는 저장되었지만 이벤트는 발행되지 않음
    kafkaTemplate.send("order.created", new OrderCreatedEvent(order));
}
```

**문제점**:
- DB 커밋 후 Kafka 발행 전 장애 → 이벤트 유실
- Kafka 발행 후 DB 커밋 실패 → 중복 이벤트
- **Two-Phase Commit 없이는 원자성 보장 불가**

### 1.2 Outbox 패턴 솔루션
```java
// ✅ 해결: Outbox 테이블을 이용한 원자적 처리
@Transactional
public void createOrder(OrderRequest request) {
    // 1. 비즈니스 엔티티 저장
    Order order = orderRepository.save(new Order(request));

    // 2. 같은 트랜잭션 내에서 Outbox에 이벤트 저장
    OrderOutbox outbox = OrderOutbox.builder()
        .aggregateId(order.getId())
        .eventType("OrderCreated")
        .payload(toJson(new OrderCreatedEvent(order)))
        .status(OutboxStatus.PENDING)
        .build();
    outboxRepository.save(outbox);

    // 3. 트랜잭션 커밋 → 두 개의 INSERT가 원자적으로 처리됨
}

// 별도 프로세스 (Outbox Relay)가 비동기로 Kafka 발행
@Scheduled(fixedDelay = 1000)
public void relayOutboxMessages() {
    List<OrderOutbox> pending = outboxRepository.findByStatusOrderByCreatedAtAsc(
        OutboxStatus.PENDING, PageRequest.of(0, 100)
    );

    for (OrderOutbox outbox : pending) {
        try {
            kafkaTemplate.send("order.created", outbox.getPayload());
            outbox.setStatus(OutboxStatus.PUBLISHED);
            outbox.setPublishedAt(LocalDateTime.now());
            outboxRepository.save(outbox);
        } catch (Exception e) {
            outbox.setRetryCount(outbox.getRetryCount() + 1);
            if (outbox.getRetryCount() > MAX_RETRIES) {
                outbox.setStatus(OutboxStatus.FAILED);
            }
            outboxRepository.save(outbox);
        }
    }
}
```

## 2. Outbox 테이블 설계

### 2.1 공통 Outbox 스키마
```sql
CREATE TABLE outbox (
    id                BIGSERIAL PRIMARY KEY,
    aggregate_type    VARCHAR(50) NOT NULL,        -- ORDER, PAYMENT, SETTLEMENT
    aggregate_id      BIGINT NOT NULL,              -- 주문ID, 결제ID 등
    event_type        VARCHAR(100) NOT NULL,        -- OrderCreated, PaymentSucceeded 등
    payload           JSONB NOT NULL,               -- 이벤트 페이로드 (JSON)
    status            VARCHAR(20) NOT NULL,         -- PENDING, PUBLISHED, FAILED
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at      TIMESTAMP,
    retry_count       INTEGER NOT NULL DEFAULT 0,
    error_message     TEXT,

    INDEX idx_status_created_at (status, created_at),  -- 폴링 쿼리 최적화
    INDEX idx_aggregate (aggregate_type, aggregate_id) -- 디버깅용
);
```

### 2.2 JPA Entity
```java
@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_status_created_at", columnList = "status,createdAt"),
    @Index(name = "idx_aggregate", columnList = "aggregateType,aggregateId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AggregateType aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = OutboxStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}

enum AggregateType {
    ORDER, PAYMENT, SETTLEMENT
}

enum OutboxStatus {
    PENDING,    // 발행 대기
    PUBLISHED,  // 발행 완료
    FAILED      // 발행 실패 (최대 재시도 초과)
}
```

## 3. Outbox Relay 구현

### 3.1 폴링 기반 Relay
```java
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(fixedDelay = 1000) // 1초마다 폴링
    @Transactional
    public void relayPendingMessages() {
        List<Outbox> pendingMessages = outboxRepository
            .findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE)
            );

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("Relaying {} outbox messages", pendingMessages.size());

        for (Outbox outbox : pendingMessages) {
            try {
                publishToKafka(outbox);
                outbox.markAsPublished();
                outboxRepository.save(outbox);

                log.info("Published: eventType={}, aggregateId={}",
                    outbox.getEventType(), outbox.getAggregateId());

            } catch (Exception e) {
                log.error("Failed to publish: eventType={}, aggregateId={}, error={}",
                    outbox.getEventType(), outbox.getAggregateId(), e.getMessage());

                outbox.incrementRetryCount();

                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    outbox.markAsFailed(e.getMessage());
                    // 알람 발송 (Slack, Email 등)
                    sendAlert(outbox, e);
                }

                outboxRepository.save(outbox);
            }
        }
    }

    private void publishToKafka(Outbox outbox) throws Exception {
        String topic = getTopicName(outbox.getEventType());
        String key = String.valueOf(outbox.getAggregateId());

        // Kafka로 발행
        kafkaTemplate.send(topic, key, outbox.getPayload())
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    throw new RuntimeException("Kafka send failed", ex);
                }
            });
    }

    private String getTopicName(String eventType) {
        // OrderCreated → order.created
        return eventType.replaceAll("([A-Z])", ".$1")
            .toLowerCase()
            .substring(1);
    }

    private void sendAlert(Outbox outbox, Exception e) {
        // Slack, Email 등으로 알람 발송
        log.error("ALERT: Outbox message failed after {} retries: {}",
            MAX_RETRY_COUNT, outbox);
    }
}
```

### 3.2 CDC 기반 Relay (Debezium - 고급)

**장점**:
- 폴링 오버헤드 없음
- 실시간 이벤트 발행
- DB 부하 감소

**구현**:
```yaml
# Debezium Connector 설정 (Kafka Connect)
name: outbox-connector
connector.class: io.debezium.connector.postgresql.PostgresConnector
database.hostname: postgres-order
database.port: 5432
database.user: order_user
database.password: order_password
database.dbname: order_db
table.include.list: public.outbox
transforms: outbox
transforms.outbox.type: io.debezium.transforms.outbox.EventRouter
transforms.outbox.table.field.event.type: event_type
transforms.outbox.table.field.event.key: aggregate_id
transforms.outbox.table.field.event.payload: payload
```

**Trade-off**:
- 운영 복잡도 증가 (Kafka Connect 추가)
- 소규모 시스템에서는 폴링 방식이 더 간단

## 4. Outbox 사용 예시

### 4.1 주문 생성 시 Outbox 기록
```java
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderResponse createOrder(OrderRequest request) {
        // 1. 비즈니스 로직: 주문 생성
        Order order = Order.builder()
            .userId(request.getUserId())
            .productId(request.getProductId())
            .quantity(request.getQuantity())
            .totalAmount(request.getTotalAmount())
            .status(OrderStatus.PENDING)
            .build();

        Order savedOrder = orderRepository.save(order);

        // 2. Outbox에 이벤트 기록 (같은 트랜잭션)
        publishOrderCreatedEvent(savedOrder);

        return OrderResponse.from(savedOrder);
    }

    private void publishOrderCreatedEvent(Order order) {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
            .orderId(order.getId())
            .userId(order.getUserId())
            .productId(order.getProductId())
            .quantity(order.getQuantity())
            .totalAmount(order.getTotalAmount())
            .createdAt(order.getCreatedAt())
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }
}
```

### 4.2 결제 성공 시 Outbox 기록
```java
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;

    public void approvePayment(Long orderId, PaymentRequest request) {
        // 1. 외부 PG 호출 (멱등성 보장)
        PgResponse pgResponse = pgClient.approve(request);

        // 2. 결제 엔티티 저장
        Payment payment = Payment.builder()
            .orderId(orderId)
            .amount(request.getAmount())
            .status(PaymentStatus.APPROVED)
            .pgTransactionId(pgResponse.getTransactionId())
            .build();

        paymentRepository.save(payment);

        // 3. Outbox에 이벤트 기록
        publishPaymentSucceededEvent(payment);
    }

    private void publishPaymentSucceededEvent(Payment payment) {
        PaymentSucceededEvent event = new PaymentSucceededEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getPgTransactionId()
        );

        Outbox outbox = Outbox.builder()
            .aggregateType(AggregateType.PAYMENT)
            .aggregateId(payment.getId())
            .eventType("PaymentSucceeded")
            .payload(toJson(event))
            .status(OutboxStatus.PENDING)
            .build();

        outboxRepository.save(outbox);
    }
}
```

## 5. Outbox 정리 (Cleanup)

### 5.1 오래된 Outbox 메시지 삭제
```java
@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanupJob {

    private final OutboxRepository outboxRepository;

    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    @Transactional
    public void cleanupOldOutboxMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        int deleted = outboxRepository.deleteByStatusAndPublishedAtBefore(
            OutboxStatus.PUBLISHED, cutoff
        );

        log.info("Cleaned up {} old outbox messages", deleted);
    }
}

// Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.status = :status AND o.publishedAt < :cutoff")
    int deleteByStatusAndPublishedAtBefore(
        @Param("status") OutboxStatus status,
        @Param("cutoff") LocalDateTime cutoff
    );
}
```

### 5.2 실패한 Outbox 모니터링
```java
@Component
@RequiredArgsConstructor
public class OutboxMonitor {

    private final OutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60000) // 1분마다
    public void monitorFailedMessages() {
        long failedCount = outboxRepository.countByStatus(OutboxStatus.FAILED);
        long pendingCount = outboxRepository.countByStatus(OutboxStatus.PENDING);

        meterRegistry.gauge("outbox.failed.count", failedCount);
        meterRegistry.gauge("outbox.pending.count", pendingCount);

        if (failedCount > 10) {
            // 알람 발송
            log.error("Too many failed outbox messages: {}", failedCount);
        }
    }
}
```

## 6. Outbox 패턴 모범 사례

### 6.1 DO
- ✅ 비즈니스 엔티티와 Outbox를 **같은 트랜잭션**에서 저장
- ✅ Outbox Relay를 **별도 스레드/프로세스**로 실행
- ✅ 이벤트 페이로드에 **모든 필요한 정보** 포함 (다른 서비스가 DB 조회 불필요)
- ✅ 멱등성 보장 (Consumer에서 중복 체크)
- ✅ 오래된 Outbox 메시지 정기적으로 삭제
- ✅ 실패한 메시지 모니터링 및 알람

### 6.2 DON'T
- ❌ Outbox 없이 직접 Kafka 발행 (트랜잭션 원자성 깨짐)
- ❌ Outbox Relay를 비즈니스 로직과 같은 트랜잭션에서 실행
- ❌ 이벤트 페이로드를 너무 크게 만들기 (> 1MB)
- ❌ Outbox 메시지를 무한정 보관 (디스크 공간 낭비)
- ❌ 재시도 없이 실패 메시지 무시

## 7. 성능 최적화

### 7.1 Batch Insert
```java
@Transactional
public void createMultipleOrders(List<OrderRequest> requests) {
    List<Order> orders = requests.stream()
        .map(req -> new Order(req))
        .collect(Collectors.toList());

    orderRepository.saveAll(orders); // Batch insert

    List<Outbox> outboxes = orders.stream()
        .map(order -> createOutbox(order))
        .collect(Collectors.toList());

    outboxRepository.saveAll(outboxes); // Batch insert
}
```

### 7.2 Polling 간격 조정
```yaml
outbox:
  relay:
    polling-interval: 1000  # 일반: 1초
    batch-size: 100         # 한 번에 100개 처리
    max-retry: 3
```

### 7.3 Index 최적화
```sql
-- 폴링 쿼리 최적화
CREATE INDEX CONCURRENTLY idx_status_created_at ON outbox (status, created_at);

-- 파티셔닝 (대용량 데이터)
CREATE TABLE outbox_2024_01 PARTITION OF outbox
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
```

## 8. 트러블슈팅

### 8.1 Outbox가 계속 PENDING 상태
**원인**: Outbox Relay가 동작하지 않음
**해결**:
- `@Scheduled` 활성화 확인 (`@EnableScheduling`)
- Relay 로그 확인
- DB 연결 상태 확인

### 8.2 중복 이벤트 발행
**원인**: Outbox Relay가 Kafka 발행 후 상태 업데이트 실패
**해결**:
- Consumer에서 멱등성 보장 (Idempotency Key)
- Kafka Producer의 `enable.idempotence=true`

### 8.3 Outbox 테이블 비대화
**원인**: Cleanup Job 미실행
**해결**:
- Cleanup Job 활성화
- 파티셔닝 또는 아카이빙 고려

## 9. 요약

| 항목 | 내용 |
|------|------|
| 목적 | DB 트랜잭션과 메시지 발행의 원자성 보장 |
| 핵심 아이디어 | Outbox 테이블에 이벤트 저장 후 별도 프로세스로 발행 |
| 장점 | 메시지 유실 방지, At-least-once 보장 |
| 단점 | 레이턴시 증가 (폴링 주기만큼), 구현 복잡도 |
| 대안 | CDC (Debezium), Transaction Log Tailing |
| 적용 대상 | 모든 이벤트 발행 (OrderCreated, PaymentSucceeded 등) |
