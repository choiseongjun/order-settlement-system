# Saga Orchestration 패턴 구현 가이드

## 1. Orchestration vs Choreography 비교

### Choreography (이벤트 기반)
- 각 서비스가 이벤트를 발행/구독하여 자율적으로 반응
- 중앙 조정자 없음
- 느슨한 결합, 높은 확장성
- **단점**: 플로우 추적 어려움, 복잡한 비즈니스 로직 구현 어려움

### Orchestration (커맨드 기반) ✅ 선택
- 중앙 Orchestrator가 각 서비스에 커맨드 전송하고 응답 수신
- 전체 플로우를 한 곳에서 관리
- 명확한 책임 분리
- **장점**: 플로우 관리 용이, 복잡한 비즈니스 로직 구현 가능

## 2. 시스템 플로우 (Order → Payment → Delivery → Settlement)

```
┌─────────────────────────────────────────────────────────────────┐
│                    Saga Orchestrator                             │
│                   (Order Service 내부)                           │
└──────┬──────────────┬──────────────┬──────────────┬─────────────┘
       │              │              │              │
       │ CreateOrder  │ ApprovePayment│ CreateDelivery│ CreateSettlement
       ▼              ▼              ▼              ▼
   ┌───────┐      ┌─────────┐   ┌──────────┐   ┌────────────┐
   │ Order │      │ Payment │   │ Delivery │   │ Settlement │
   │Service│      │ Service │   │ Service  │   │  Service   │
   └───┬───┘      └────┬────┘   └────┬─────┘   └─────┬──────┘
       │               │             │               │
       │ OrderCreated  │ PaymentApproved│ DeliveryCreated│ SettlementCreated
       └───────────────┴─────────────┴───────────────┘
                    (응답을 Orchestrator가 수신)
```

### 2.1 정상 플로우

```
1. [Client] → POST /api/orders (주문 생성 요청)

2. [Order Service - Orchestrator]
   └─> 주문 생성 (status: PENDING)
   └─> Saga 인스턴스 생성
   └─> Payment Service에 커맨드 발행: "ApprovePaymentCommand"

3. [Payment Service]
   └─> Kafka Consumer: ApprovePaymentCommand 수신
   └─> 결제 승인 처리 (PG 연동)
   └─> 응답 발행: "PaymentApprovedReply" or "PaymentFailedReply"

4. [Order Service - Orchestrator]
   └─> PaymentApprovedReply 수신
   └─> 주문 상태 업데이트 (PAYMENT_APPROVED)
   └─> Delivery Service에 커맨드 발행: "CreateDeliveryCommand"

5. [Delivery Service]
   └─> Kafka Consumer: CreateDeliveryCommand 수신
   └─> 배송 정보 생성
   └─> 응답 발행: "DeliveryCreatedReply"

6. [Order Service - Orchestrator]
   └─> DeliveryCreatedReply 수신
   └─> 주문 상태 업데이트 (CONFIRMED)
   └─> Settlement Service에 커맨드 발행: "CreateSettlementCommand"

7. [Settlement Service]
   └─> Kafka Consumer: CreateSettlementCommand 수신
   └─> 정산 대상 추가
   └─> 응답 발행: "SettlementCreatedReply"

8. [Order Service - Orchestrator]
   └─> SettlementCreatedReply 수신
   └─> Saga 완료 (status: COMPLETED)
```

### 2.2 보상 트랜잭션 플로우 (결제 실패 시)

```
1. [Payment Service] 결제 승인 실패
   └─> "PaymentFailedReply" 발행

2. [Order Service - Orchestrator]
   └─> PaymentFailedReply 수신
   └─> Saga 상태를 COMPENSATING으로 변경
   └─> 보상 트랜잭션 시작: 주문 취소
   └─> 주문 상태 업데이트 (CANCELLED)
   └─> Saga 완료 (status: ABORTED)
```

### 2.3 보상 트랜잭션 플로우 (배송 생성 실패 시)

```
1. [Delivery Service] 배송 생성 실패
   └─> "DeliveryFailedReply" 발행

2. [Order Service - Orchestrator]
   └─> DeliveryFailedReply 수신
   └─> Saga 상태를 COMPENSATING으로 변경

   보상 단계 1: 결제 취소
   └─> Payment Service에 커맨드 발행: "CancelPaymentCommand"

3. [Payment Service]
   └─> CancelPaymentCommand 수신
   └─> 결제 취소 처리 (PG 취소 API 호출)
   └─> 응답 발행: "PaymentCancelledReply"

4. [Order Service - Orchestrator]
   └─> PaymentCancelledReply 수신

   보상 단계 2: 주문 취소
   └─> 주문 상태 업데이트 (CANCELLED)
   └─> Saga 완료 (status: ABORTED)
```

## 3. Kafka 토픽 구조

### 3.1 Command Topics (Orchestrator → Participants)

```
saga.command.payment.approve      # 결제 승인 요청
saga.command.payment.cancel       # 결제 취소 요청
saga.command.delivery.create      # 배송 생성 요청
saga.command.delivery.cancel      # 배송 취소 요청
saga.command.settlement.create    # 정산 생성 요청
```

### 3.2 Reply Topics (Participants → Orchestrator)

```
saga.reply.payment                # 결제 서비스 응답
saga.reply.delivery               # 배송 서비스 응답
saga.reply.settlement             # 정산 서비스 응답
```

## 4. 핵심 데이터 모델

### 4.1 Saga Instance (Orchestrator가 관리)

```java
@Entity
@Table(name = "saga_instance")
public class SagaInstance {
    @Id
    private String sagaId;              // UUID

    private String sagaType;            // ORDER_FULFILLMENT_SAGA

    private Long orderId;               // 주문 ID

    @Enumerated(EnumType.STRING)
    private SagaStatus status;          // STARTED, COMPENSATING, COMPLETED, ABORTED

    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;       // ORDER_CREATED, PAYMENT_APPROVED, 등

    @Column(columnDefinition = "JSONB")
    private String payload;             // Saga 컨텍스트 (주문 정보, 결제 정보 등)

    @Column(columnDefinition = "JSONB")
    private String compensationData;    // 보상에 필요한 데이터 (결제ID, 배송ID 등)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

enum SagaStatus {
    STARTED,        // Saga 시작
    COMPENSATING,   // 보상 트랜잭션 진행 중
    COMPLETED,      // Saga 성공 완료
    ABORTED         // Saga 실패 (보상 완료)
}

enum SagaStep {
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
```

### 4.2 Saga Commands (Orchestrator → Participant)

```java
// Base Command
public interface SagaCommand {
    String getSagaId();
    String getCommandType();
}

// 결제 승인 커맨드
@Data
@Builder
public class ApprovePaymentCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String paymentMethod;

    @Override
    public String getCommandType() {
        return "ApprovePayment";
    }
}

// 결제 취소 커맨드 (보상)
@Data
@Builder
public class CancelPaymentCommand implements SagaCommand {
    private String sagaId;
    private Long paymentId;
    private String reason;

    @Override
    public String getCommandType() {
        return "CancelPayment";
    }
}

// 배송 생성 커맨드
@Data
@Builder
public class CreateDeliveryCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long userId;
    private String address;
    private String recipientName;
    private String recipientPhone;

    @Override
    public String getCommandType() {
        return "CreateDelivery";
    }
}

// 배송 취소 커맨드 (보상)
@Data
@Builder
public class CancelDeliveryCommand implements SagaCommand {
    private String sagaId;
    private Long deliveryId;
    private String reason;

    @Override
    public String getCommandType() {
        return "CancelDelivery";
    }
}

// 정산 생성 커맨드
@Data
@Builder
public class CreateSettlementCommand implements SagaCommand {
    private String sagaId;
    private Long orderId;
    private Long paymentId;
    private BigDecimal amount;

    @Override
    public String getCommandType() {
        return "CreateSettlement";
    }
}
```

### 4.3 Saga Replies (Participant → Orchestrator)

```java
// Base Reply
public interface SagaReply {
    String getSagaId();
    String getReplyType();
    boolean isSuccess();
}

// 결제 승인 성공 응답
@Data
@Builder
public class PaymentApprovedReply implements SagaReply {
    private String sagaId;
    private Long paymentId;
    private String pgTransactionId;

    @Override
    public String getReplyType() {
        return "PaymentApproved";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}

// 결제 실패 응답
@Data
@Builder
public class PaymentFailedReply implements SagaReply {
    private String sagaId;
    private String reason;
    private String errorCode;

    @Override
    public String getReplyType() {
        return "PaymentFailed";
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}

// 결제 취소 완료 응답
@Data
@Builder
public class PaymentCancelledReply implements SagaReply {
    private String sagaId;
    private Long paymentId;

    @Override
    public String getReplyType() {
        return "PaymentCancelled";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}

// 배송 생성 성공 응답
@Data
@Builder
public class DeliveryCreatedReply implements SagaReply {
    private String sagaId;
    private Long deliveryId;
    private String trackingNumber;

    @Override
    public String getReplyType() {
        return "DeliveryCreated";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}

// 배송 실패 응답
@Data
@Builder
public class DeliveryFailedReply implements SagaReply {
    private String sagaId;
    private String reason;

    @Override
    public String getReplyType() {
        return "DeliveryFailed";
    }

    @Override
    public boolean isSuccess() {
        return false;
    }
}

// 정산 생성 성공 응답
@Data
@Builder
public class SettlementCreatedReply implements SagaReply {
    private String sagaId;
    private Long settlementId;

    @Override
    public String getReplyType() {
        return "SettlementCreated";
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
```

## 5. Saga Orchestrator 구현

### 5.1 Saga Orchestrator (Order Service)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderFulfillmentSagaOrchestrator {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Saga 시작: 주문 생성 후 Saga 인스턴스 생성 및 결제 요청
     */
    @Transactional
    public SagaInstance startSaga(Order order) {
        // 1. Saga 인스턴스 생성
        SagaInstance saga = SagaInstance.builder()
            .sagaId(UUID.randomUUID().toString())
            .sagaType("ORDER_FULFILLMENT_SAGA")
            .orderId(order.getId())
            .status(SagaStatus.STARTED)
            .currentStep(SagaStep.ORDER_CREATED)
            .payload(createPayload(order))
            .compensationData("{}")
            .build();

        sagaInstanceRepository.save(saga);

        log.info("Saga started: sagaId={}, orderId={}", saga.getSagaId(), order.getId());

        // 2. 첫 번째 단계: 결제 승인 요청
        sendApprovePaymentCommand(saga, order);

        // 3. Saga 상태 업데이트
        saga.updateStep(SagaStep.PAYMENT_REQUESTED);
        sagaInstanceRepository.save(saga);

        return saga;
    }

    /**
     * 결제 승인 커맨드 발행
     */
    private void sendApprovePaymentCommand(SagaInstance saga, Order order) {
        ApprovePaymentCommand command = ApprovePaymentCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .userId(order.getUserId())
            .amount(order.getTotalAmount())
            .paymentMethod("CARD") // 실제로는 주문에서 가져와야 함
            .build();

        kafkaTemplate.send("saga.command.payment.approve",
            saga.getSagaId(),
            toJson(command));

        log.info("ApprovePaymentCommand sent: sagaId={}", saga.getSagaId());
    }

    /**
     * 결제 승인 성공 처리
     */
    @Transactional
    public void handlePaymentApproved(PaymentApprovedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow(() -> new SagaNotFoundException(reply.getSagaId()));

        // 1. 보상 데이터에 결제 ID 저장
        saga.addCompensationData("paymentId", reply.getPaymentId());

        // 2. 주문 상태 업데이트
        Order order = orderRepository.findById(saga.getOrderId())
            .orElseThrow();
        order.updateStatus(OrderStatus.PAYMENT_APPROVED);
        orderRepository.save(order);

        // 3. Saga 다음 단계: 배송 생성 요청
        saga.updateStep(SagaStep.PAYMENT_APPROVED);
        sagaInstanceRepository.save(saga);

        sendCreateDeliveryCommand(saga, order);

        saga.updateStep(SagaStep.DELIVERY_REQUESTED);
        sagaInstanceRepository.save(saga);

        log.info("Payment approved, delivery requested: sagaId={}", saga.getSagaId());
    }

    /**
     * 결제 실패 처리 (보상 트랜잭션)
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow();

        log.error("Payment failed: sagaId={}, reason={}", saga.getSagaId(), reply.getReason());

        // 1. Saga 보상 모드로 전환
        saga.updateStatus(SagaStatus.COMPENSATING);

        // 2. 주문 취소
        Order order = orderRepository.findById(saga.getOrderId())
            .orElseThrow();
        order.cancel("Payment failed: " + reply.getReason());
        orderRepository.save(order);

        // 3. Saga 중단 완료
        saga.updateStep(SagaStep.ORDER_CANCELLED);
        saga.updateStatus(SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);

        log.info("Saga aborted due to payment failure: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 커맨드 발행
     */
    private void sendCreateDeliveryCommand(SagaInstance saga, Order order) {
        CreateDeliveryCommand command = CreateDeliveryCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .userId(order.getUserId())
            .address("서울시 강남구 테헤란로") // 실제로는 주문에서 가져와야 함
            .recipientName("홍길동")
            .recipientPhone("010-1234-5678")
            .build();

        kafkaTemplate.send("saga.command.delivery.create",
            saga.getSagaId(),
            toJson(command));

        log.info("CreateDeliveryCommand sent: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 성공 처리
     */
    @Transactional
    public void handleDeliveryCreated(DeliveryCreatedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow();

        // 1. 보상 데이터에 배송 ID 저장
        saga.addCompensationData("deliveryId", reply.getDeliveryId());

        // 2. 주문 상태 업데이트
        Order order = orderRepository.findById(saga.getOrderId())
            .orElseThrow();
        order.updateStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        // 3. Saga 다음 단계: 정산 생성 요청
        saga.updateStep(SagaStep.DELIVERY_CREATED);
        sagaInstanceRepository.save(saga);

        sendCreateSettlementCommand(saga, order);

        saga.updateStep(SagaStep.SETTLEMENT_REQUESTED);
        sagaInstanceRepository.save(saga);

        log.info("Delivery created, settlement requested: sagaId={}", saga.getSagaId());
    }

    /**
     * 배송 생성 실패 처리 (보상 트랜잭션)
     */
    @Transactional
    public void handleDeliveryFailed(DeliveryFailedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow();

        log.error("Delivery failed: sagaId={}, reason={}", saga.getSagaId(), reply.getReason());

        // 1. Saga 보상 모드로 전환
        saga.updateStatus(SagaStatus.COMPENSATING);
        saga.updateStep(SagaStep.PAYMENT_CANCELLING);
        sagaInstanceRepository.save(saga);

        // 2. 결제 취소 커맨드 발행 (보상)
        Long paymentId = saga.getCompensationDataAs("paymentId", Long.class);
        sendCancelPaymentCommand(saga, paymentId, "Delivery failed");
    }

    /**
     * 결제 취소 커맨드 발행 (보상)
     */
    private void sendCancelPaymentCommand(SagaInstance saga, Long paymentId, String reason) {
        CancelPaymentCommand command = CancelPaymentCommand.builder()
            .sagaId(saga.getSagaId())
            .paymentId(paymentId)
            .reason(reason)
            .build();

        kafkaTemplate.send("saga.command.payment.cancel",
            saga.getSagaId(),
            toJson(command));

        log.info("CancelPaymentCommand sent: sagaId={}, paymentId={}",
            saga.getSagaId(), paymentId);
    }

    /**
     * 결제 취소 완료 처리
     */
    @Transactional
    public void handlePaymentCancelled(PaymentCancelledReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow();

        // 1. 주문 취소
        Order order = orderRepository.findById(saga.getOrderId())
            .orElseThrow();
        order.cancel("Delivery creation failed");
        orderRepository.save(order);

        // 2. Saga 중단 완료
        saga.updateStep(SagaStep.ORDER_CANCELLED);
        saga.updateStatus(SagaStatus.ABORTED);
        sagaInstanceRepository.save(saga);

        log.info("Saga aborted, all compensations completed: sagaId={}", saga.getSagaId());
    }

    /**
     * 정산 생성 커맨드 발행
     */
    private void sendCreateSettlementCommand(SagaInstance saga, Order order) {
        Long paymentId = saga.getCompensationDataAs("paymentId", Long.class);

        CreateSettlementCommand command = CreateSettlementCommand.builder()
            .sagaId(saga.getSagaId())
            .orderId(order.getId())
            .paymentId(paymentId)
            .amount(order.getTotalAmount())
            .build();

        kafkaTemplate.send("saga.command.settlement.create",
            saga.getSagaId(),
            toJson(command));

        log.info("CreateSettlementCommand sent: sagaId={}", saga.getSagaId());
    }

    /**
     * 정산 생성 성공 처리 (Saga 완료)
     */
    @Transactional
    public void handleSettlementCreated(SettlementCreatedReply reply) {
        SagaInstance saga = sagaInstanceRepository.findById(reply.getSagaId())
            .orElseThrow();

        // Saga 완료
        saga.updateStep(SagaStep.SETTLEMENT_CREATED);
        saga.updateStatus(SagaStatus.COMPLETED);
        sagaInstanceRepository.save(saga);

        log.info("Saga completed successfully: sagaId={}", saga.getSagaId());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private String createPayload(Order order) {
        // 주문 정보를 JSON으로 직렬화
        return toJson(order);
    }
}
```

## 6. 장점 및 고려사항

### 장점
✅ **명확한 플로우 관리** - Orchestrator가 전체 플로우를 제어
✅ **쉬운 디버깅** - Saga Instance 테이블에서 현재 상태 확인 가능
✅ **보상 트랜잭션 관리 용이** - 역순으로 보상 커맨드 발행
✅ **복잡한 비즈니스 로직** - 조건부 분기, 병렬 처리 등 구현 가능

### 고려사항
⚠️ **Orchestrator SPOF** - Orchestrator 장애 시 Saga 진행 중단 (고가용성 필요)
⚠️ **결합도 증가** - Orchestrator가 모든 서비스의 API를 알아야 함
⚠️ **Orchestrator 복잡도** - 서비스가 늘어날수록 Orchestrator 복잡도 증가

## 7. 타임아웃 처리

```java
@Scheduled(fixedDelay = 60000) // 1분마다
@Transactional
public void handleSagaTimeouts() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);

    List<SagaInstance> timedOutSagas = sagaInstanceRepository
        .findByStatusAndCreatedAtBefore(SagaStatus.STARTED, cutoff);

    for (SagaInstance saga : timedOutSagas) {
        log.warn("Saga timeout detected: sagaId={}, currentStep={}",
            saga.getSagaId(), saga.getCurrentStep());

        // 보상 트랜잭션 시작
        compensateSaga(saga);
    }
}
```

## 요약

- **Orchestrator**: Order Service가 전체 플로우 관리
- **Participants**: Payment, Delivery, Settlement Service가 커맨드 수신 및 응답
- **통신**: Kafka Command/Reply 토픽 사용
- **보상**: 역순으로 취소 커맨드 발행 (Delivery 실패 → Payment 취소 → Order 취소)
