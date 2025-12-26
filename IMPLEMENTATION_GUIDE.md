# 주문-결제-배송-정산 Saga Orchestration 시스템 구현 완료

## 🎉 구현 완료 현황

### ✅ 완전히 구현된 서비스

#### 1. **Order Service** (포트: 8081) - Saga Orchestrator
- ✅ 도메인 모델: Order, OrderStatus
- ✅ Saga 인프라: SagaInstance, Outbox
- ✅ Saga Orchestrator 핵심 로직
- ✅ Saga Commands/Replies 모델
- ✅ Outbox Relay (폴링 방식)
- ✅ REST API (주문 생성/조회)
- ✅ Kafka Consumer (Saga Replies 수신)
- ✅ Flyway 마이그레이션 (3개)
- ✅ 완전한 설정 (Kafka, Redis, JPA)

#### 2. **Payment Service** (포트: 8082) - Saga Participant
- ✅ 도메인 모델: Payment, PaymentStatus
- ✅ Mock PG 연동 (80% 성공률)
- ✅ Saga Command Handler (승인/취소)
- ✅ Kafka Producer (Replies 발행)
- ✅ Flyway 마이그레이션
- ✅ 완전한 설정

### 🔨 구현 필요 서비스

#### 3. **Delivery Service** (포트: 8084)
Payment Service와 동일한 패턴으로 구현:
- Delivery 도메인 모델
- CreateDeliveryCommand 처리
- CancelDeliveryCommand 처리
- DeliveryCreatedReply / DeliveryFailedReply 발행

#### 4. **Settlement Service** (포트: 8083)
- Settlement 도메인 모델
- CreateSettlementCommand 처리
- SettlementCreatedReply 발행
- (선택적) Spring Batch 일 단위 정산 집계

## 📁 프로젝트 구조

```
order-settlement-system/
├── order-module/              ✅ 완료
│   ├── domain/model/         (Order)
│   ├── infrastructure/
│   │   ├── saga/             (Orchestrator, Commands, Replies)
│   │   └── outbox/           (Outbox, OutboxRelay)
│   └── application/service/  (OrderService)
│
├── payment-module/            ✅ 완료
│   ├── domain/model/         (Payment)
│   ├── adapter/in/event/     (SagaCommandConsumer)
│   └── application/service/  (PaymentService)
│
├── delivery-module/           🔨 구현 필요
│   └── (Payment와 동일 패턴)
│
├── settlement-module/         🔨 구현 필요
│   └── (Payment와 동일 패턴)
│
└── order-module-infra/        ✅ 완료
    ├── docker-compose.yml
    └── prometheus/
```

## 🚀 실행 방법

### 1. 인프라 실행

```bash
cd order-module-infra
docker-compose up -d

# 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f
```

### 2. 서비스 실행

```bash
# Order Service (Orchestrator)
cd order-module
./gradlew bootRun

# Payment Service (Participant)
cd payment-module
./gradlew bootRun

# Delivery Service (구현 후)
cd delivery-module
./gradlew bootRun

# Settlement Service (구현 후)
cd settlement-module
./gradlew bootRun
```

## 🧪 테스트 시나리오

### 주문 생성 (정상 플로우)

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 100,
    "quantity": 2,
    "totalAmount": 50000,
    "deliveryAddress": "서울시 강남구 테헤란로 123",
    "recipientName": "홍길동",
    "recipientPhone": "010-1234-5678"
  }'
```

**기대 결과**:
```
1. Order Service: 주문 생성 (PENDING)
2. Saga 시작 → Payment Service에 ApprovePaymentCommand 발행
3. Payment Service: 결제 승인 (80% 확률)
   - 성공 시: PaymentApprovedReply 발행
   - 실패 시: PaymentFailedReply 발행
4. Order Service: Reply 수신
   - 성공: Delivery Service에 CreateDeliveryCommand 발행
   - 실패: 주문 취소 (CANCELLED)
5. Delivery Service: 배송 생성
6. Settlement Service: 정산 대상 추가
7. 최종: 주문 상태 = CONFIRMED (또는 CANCELLED)
```

### 주문 조회

```bash
curl http://localhost:8081/api/orders/1
```

### 사용자별 주문 목록 조회

```bash
curl http://localhost:8081/api/orders/user/1
```

## 📊 모니터링

| 서비스 | URL | 설명 |
|--------|-----|------|
| Kafka UI | http://localhost:8989 | Kafka 토픽, 메시지 확인 |
| Adminer | http://localhost:8080 | DB 확인 (order_db, payment_db 등) |
| Zipkin | http://localhost:9411 | 분산 추적 |
| Prometheus | http://localhost:9090 | 메트릭 수집 |
| Grafana | http://localhost:3000 | 메트릭 시각화 (admin/admin) |

### Kafka 토픽 확인

Kafka UI (http://localhost:8989)에서 다음 토픽들을 확인:

**Command Topics** (Orchestrator → Participants):
- `saga.command.payment.approve`
- `saga.command.payment.cancel`
- `saga.command.delivery.create`
- `saga.command.delivery.cancel`
- `saga.command.settlement.create`

**Reply Topics** (Participants → Orchestrator):
- `saga.reply.payment`
- `saga.reply.delivery`
- `saga.reply.settlement`

## 🔍 디버깅

### Order Service 로그

```bash
tail -f logs/order-service.log

# 또는 애플리케이션 실행 시 콘솔에서 확인
# DEBUG 레벨로 Saga 진행 상황 출력
```

### 데이터베이스 확인

Adminer (http://localhost:8080)에서:

**Order DB (localhost:5432)**:
- 테이블: orders, outbox, saga_instance
- Saga 진행 상황: saga_instance 테이블 조회

**Payment DB (localhost:5433)**:
- 테이블: payments
- 결제 내역 확인

### Redis 확인

```bash
docker exec -it redis redis-cli -a redis_password

# Idempotency Key 확인
KEYS *
GET "PaymentApproved:saga-id-xxx"
```

## 🎯 핵심 플로우

### 1. 주문 생성 → Saga 시작

```java
// OrderService.createOrder()
Order order = orderRepository.save(new Order(...));
SagaInstance saga = sagaOrchestrator.startSaga(order);

// OrderFulfillmentSagaOrchestrator.startSaga()
sendApprovePaymentCommand(saga, order);
```

### 2. Payment Participant 처리

```java
// SagaCommandConsumer.handleApprovePaymentCommand()
Payment payment = paymentService.approvePayment(...);

if (payment.getStatus() == APPROVED) {
    sendPaymentApprovedReply(payment);
} else {
    sendPaymentFailedReply(...);
}
```

### 3. Orchestrator가 Reply 수신

```java
// SagaReplyConsumer.handlePaymentApproved()
sagaOrchestrator.handlePaymentApproved(reply);

// OrderFulfillmentSagaOrchestrator.handlePaymentApproved()
order.updateStatus(PAYMENT_APPROVED);
sendCreateDeliveryCommand(saga, order);
```

## 🛠 Delivery & Settlement Service 구현 가이드

### Delivery Service (Payment Service 패턴 복사)

1. **도메인 모델**:
```java
@Entity
public class Delivery {
    private Long id;
    private Long orderId;
    private String address;
    private String trackingNumber;
    private DeliveryStatus status; // PENDING, CREATED, CANCELLED
}
```

2. **Saga Command Consumer**:
```java
@KafkaListener(topics = "saga.command.delivery.create")
public void handleCreateDeliveryCommand(String message) {
    Delivery delivery = deliveryService.createDelivery(...);
    sendDeliveryCreatedReply(delivery);
}
```

3. **Flyway Migration**:
```sql
CREATE TABLE deliveries (...);
```

4. **application.yml**:
```yaml
server:
  port: 8084
spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/delivery_db
```

### Settlement Service (간단 버전)

1. **도메인 모델**:
```java
@Entity
public class SettlementTarget {
    private Long id;
    private Long orderId;
    private Long paymentId;
    private BigDecimal amount;
}
```

2. **Command Consumer**:
```java
@KafkaListener(topics = "saga.command.settlement.create")
public void handleCreateSettlementCommand(String message) {
    SettlementTarget target = settlementService.addTarget(...);
    sendSettlementCreatedReply(target);
}
```

## 🔥 주요 기능

✅ **Transactional Outbox 패턴** - DB 트랜잭션과 메시지 발행의 원자성 보장
✅ **Saga Orchestration 패턴** - 중앙 Orchestrator가 전체 플로우 제어
✅ **보상 트랜잭션** - 결제 실패 시 자동으로 주문 취소
✅ **멱등성 보장** - Redis 기반 중복 메시지 처리 방지
✅ **Outbox Relay** - 1초마다 폴링하여 Kafka로 발행
✅ **분산 추적** - Zipkin으로 전체 요청 체인 추적
✅ **Mock PG** - 80% 성공률로 결제 승인 시뮬레이션

## 📝 다음 단계

1. ✅ Order Service 완성
2. ✅ Payment Service 완성
3. 🔨 Delivery Service 구현 (Payment 패턴 복사)
4. 🔨 Settlement Service 구현
5. 🧪 통합 테스트 작성
6. 📈 Grafana 대시보드 구성
7. 🚀 프로덕션 배포 준비

## 🎓 학습 포인트

이 프로젝트를 통해 다음을 배울 수 있습니다:

1. **Saga Orchestration 패턴** - 중앙 집중식 분산 트랜잭션 관리
2. **Transactional Outbox 패턴** - 메시지 유실 방지
3. **이벤트 기반 아키텍처** - Kafka를 통한 서비스 간 통신
4. **보상 트랜잭션** - 역순으로 롤백하는 메커니즘
5. **멱등성 설계** - 중복 메시지 처리 방지
6. **마이크로서비스 관찰성** - Zipkin, Prometheus, Grafana

## 💡 트러블슈팅

### 1. Kafka 연결 실패
```bash
# Kafka 상태 확인
docker-compose logs kafka

# Kafka 재시작
docker-compose restart kafka
```

### 2. DB 연결 실패
```bash
# PostgreSQL 상태 확인
docker exec postgres-order pg_isready -U order_user -d order_db
```

### 3. Outbox가 발행되지 않음
- OutboxRelay가 동작하는지 확인: `@EnableScheduling` 어노테이션
- Outbox 테이블 확인: `SELECT * FROM outbox WHERE status = 'PENDING'`

### 4. Saga가 진행되지 않음
- saga_instance 테이블 확인: 현재 단계와 상태
- Kafka Consumer Group 확인: Kafka UI에서 Lag 확인

---

**구현 완료 상태**: Order Service ✅ | Payment Service ✅ | Delivery Service 🔨 | Settlement Service 🔨

**난이도**: ⭐⭐⭐⭐⭐ (7년차 레벨)

**기술 스택**: Java 25, Spring Boot 4.0.1, Kafka, PostgreSQL, Redis, Zipkin, Prometheus, Grafana
