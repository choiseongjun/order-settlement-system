# 주문-결제-정산 마이크로서비스 아키텍처 설계

## 1. 시스템 개요

7년차 레벨의 엔터프라이즈급 분산 시스템으로, Transactional Outbox 패턴과 Saga 패턴을 활용한 최종 일관성(Eventual Consistency)을 보장하는 커머스 도메인 마이크로서비스입니다.

### 핵심 설계 원칙
- **최종 일관성**: Outbox 패턴을 통한 안정적인 이벤트 발행
- **분산 트랜잭션**: Saga 패턴을 통한 비즈니스 트랜잭션 관리
- **멱등성**: 중복 메시지 처리를 위한 Idempotency Key 관리
- **장애 복구**: DLQ, 재시도, 보상 트랜잭션
- **관찰성**: 분산 추적, 메트릭, 로깅

## 2. 서비스 구성

### 2.1 Order Service (주문 서비스)
**포트**: 8081
**데이터베이스**: PostgreSQL (port 5432)

**책임**:
- 주문 생성, 조회, 취소
- 주문 상태 관리 (PENDING → CONFIRMED → CANCELLED)
- Outbox 테이블에 이벤트 발행
- Saga Orchestrator 역할 (또는 Saga Participant)

**주요 엔티티**:
```
Order
- id: Long (PK)
- userId: Long
- productId: Long
- quantity: Integer
- totalAmount: BigDecimal
- status: OrderStatus (PENDING, PAYMENT_REQUESTED, CONFIRMED, CANCELLED, FAILED)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
- version: Long (낙관적 락)

OrderOutbox
- id: Long (PK)
- aggregateType: String (ORDER)
- aggregateId: Long
- eventType: String (OrderCreated, OrderCancelled)
- payload: JSON
- status: OutboxStatus (PENDING, PUBLISHED, FAILED)
- createdAt: LocalDateTime
- publishedAt: LocalDateTime
- retryCount: Integer
```

**발행 이벤트**:
- `OrderCreated`: 주문 생성 완료
- `OrderCancelled`: 주문 취소
- `OrderConfirmed`: 결제 완료 후 주문 확정
- `OrderFailed`: 주문 실패

**구독 이벤트**:
- `PaymentSucceeded`: 결제 성공 → 주문 확정
- `PaymentFailed`: 결제 실패 → 주문 취소

### 2.2 Payment Service (결제 서비스)
**포트**: 8082
**데이터베이스**: PostgreSQL (port 5433)

**책임**:
- 결제 승인/취소 처리
- 외부 PG 연동 (Mock 또는 실제 PG)
- 결제 이력 관리
- 멱등성 보장 (중복 결제 방지)

**주요 엔티티**:
```
Payment
- id: Long (PK)
- orderId: Long
- userId: Long
- amount: BigDecimal
- paymentMethod: PaymentMethod (CARD, BANK_TRANSFER, VIRTUAL_ACCOUNT)
- status: PaymentStatus (PENDING, APPROVED, FAILED, CANCELLED)
- pgTransactionId: String
- idempotencyKey: String (UNIQUE)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime

PaymentOutbox
- (Order Outbox와 동일 구조)
```

**발행 이벤트**:
- `PaymentSucceeded`: 결제 승인 성공
- `PaymentFailed`: 결제 승인 실패
- `PaymentCancelled`: 결제 취소 완료

**구독 이벤트**:
- `OrderCreated`: 주문 생성 → 결제 승인 시도
- `OrderCancelled`: 주문 취소 → 결제 취소 (보상 트랜잭션)

### 2.3 Settlement Service (정산 서비스)
**포트**: 8083
**데이터베이스**: PostgreSQL (port 5434)

**책임**:
- 일 단위 정산 집계
- 매출 리포트 생성
- 판매자별 정산 내역 관리
- 배치 프로세싱 (Spring Batch)

**주요 엔티티**:
```
Settlement
- id: Long (PK)
- sellerId: Long
- settlementDate: LocalDate
- totalOrderCount: Integer
- totalAmount: BigDecimal
- totalFee: BigDecimal
- netAmount: BigDecimal
- status: SettlementStatus (PENDING, COMPLETED, FAILED)
- createdAt: LocalDateTime

SettlementDetail
- id: Long (PK)
- settlementId: Long (FK)
- orderId: Long
- paymentId: Long
- amount: BigDecimal
- fee: BigDecimal
- netAmount: BigDecimal

SettlementOutbox
- (Order Outbox와 동일 구조)
```

**발행 이벤트**:
- `SettlementCompleted`: 정산 완료

**구독 이벤트**:
- `PaymentSucceeded`: 결제 성공 → 정산 대상 추가

## 3. 통신 패턴

### 3.1 동기 통신 (REST API)
- 클라이언트 → Order Service: 주문 생성/조회
- 내부 서비스간 동기 호출은 **최소화** (Circuit Breaker 적용)

### 3.2 비동기 통신 (Kafka)
- 모든 서비스간 이벤트는 Kafka를 통해 전달
- At-least-once 전달 보장
- Consumer Group을 통한 부하 분산

### 3.3 Outbox Pattern Flow
```
1. 비즈니스 로직 실행 (예: 주문 생성)
2. 같은 트랜잭션 내에서 Outbox 테이블에 이벤트 INSERT
3. 트랜잭션 커밋
4. Outbox Relay (별도 프로세스)가 Outbox 테이블 폴링
5. Kafka로 이벤트 발행
6. Outbox 레코드 상태를 PUBLISHED로 업데이트
7. Consumer가 이벤트 수신 및 처리
```

## 4. Saga 패턴 설계

### 4.1 선택: Choreography vs Orchestration

**선택: Choreography (코레오그래피)**
이유:
- 서비스 간 결합도 최소화
- 각 서비스가 자율적으로 이벤트 구독/발행
- 확장성이 높음
- 3개 서비스로 복잡도가 낮아 오케스트레이터 불필요

### 4.2 주문 생성 Saga Flow

**정상 플로우**:
```
1. [Order Service] 주문 생성 (상태: PENDING)
   └─> OrderCreated 이벤트 발행

2. [Payment Service] OrderCreated 구독
   └─> 결제 승인 시도
   └─> PaymentSucceeded 이벤트 발행 (성공 시)

3. [Order Service] PaymentSucceeded 구독
   └─> 주문 상태를 CONFIRMED로 변경

4. [Settlement Service] PaymentSucceeded 구독
   └─> 정산 대상 추가
```

**보상 트랜잭션 플로우** (결제 실패 시):
```
1. [Payment Service] 결제 승인 실패
   └─> PaymentFailed 이벤트 발행

2. [Order Service] PaymentFailed 구독
   └─> 주문 상태를 CANCELLED로 변경
   └─> OrderCancelled 이벤트 발행

3. [Payment Service] OrderCancelled 구독
   └─> 결제 취소 (이미 실패했으므로 skip 가능)
```

### 4.3 Saga 상태 관리

각 서비스는 자신의 Saga 참여 상태를 로컬 DB에 저장:
```
SagaInstance
- id: String (UUID)
- sagaType: String (ORDER_PAYMENT_SAGA)
- status: SagaStatus (STARTED, COMPENSATING, COMPLETED, ABORTED)
- currentStep: String
- payload: JSON
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

## 5. 멱등성 보장

### 5.1 Producer 멱등성
- Kafka Producer의 `enable.idempotence=true`
- Transactional Producer 사용

### 5.2 Consumer 멱등성
- **Idempotency Key** 사용 (Redis 또는 DB)
- 이벤트 처리 전 중복 체크:
  ```
  IdempotencyRecord
  - key: String (eventId 또는 orderId + eventType)
  - processedAt: LocalDateTime
  - TTL: 7일
  ```

### 5.3 중복 메시지 처리 전략
```java
@Transactional
public void handleOrderCreated(OrderCreatedEvent event) {
    String idempotencyKey = event.getEventId();

    // 1. Redis에서 중복 체크 (빠른 응답)
    if (idempotencyService.isProcessed(idempotencyKey)) {
        log.info("Duplicate event ignored: {}", idempotencyKey);
        return;
    }

    // 2. 비즈니스 로직 실행
    processPayment(event);

    // 3. Idempotency Key 저장 (같은 트랜잭션)
    idempotencyService.markAsProcessed(idempotencyKey);
}
```

## 6. 재시도 & DLQ 전략

### 6.1 재시도 정책
- **즉시 재시도**: 최대 3회, 지수 백오프 (1s, 2s, 4s)
- **지연 재시도**: DLQ로 이동 후 별도 배치로 재처리

### 6.2 DLQ 처리
```
Kafka Topic 구조:
- order.created           (메인 토픽)
- order.created.retry     (재시도 토픽)
- order.created.dlq       (DLQ)
```

### 6.3 DLQ 재처리 배치
- 매 시간마다 DLQ 메시지 검토
- 재시도 가능한 오류 (네트워크, 일시적 장애) → 재발행
- 재시도 불가능한 오류 (비즈니스 오류) → 알람 발송 및 수동 처리

## 7. 장애 시나리오

### 7.1 네트워크 파티션
**시나리오**: Payment Service가 일시적으로 응답 불가
**대응**:
- Circuit Breaker 오픈
- 주문은 PENDING 상태로 유지
- PaymentFailed 이벤트 발행 후 보상 트랜잭션

### 7.2 Kafka Broker 장애
**시나리오**: Kafka 클러스터 다운
**대응**:
- Outbox 레코드가 PENDING 상태로 누적
- Kafka 복구 후 Outbox Relay가 자동으로 발행 재개
- 메시지 유실 없음 (At-least-once 보장)

### 7.3 Consumer 처리 실패
**시나리오**: Payment Service의 PG 연동 실패
**대응**:
- 재시도 3회 후 DLQ로 이동
- 알람 발송 (Slack, Email)
- 수동 또는 배치로 재처리

### 7.4 Database 장애
**시나리오**: Order DB 장애
**대응**:
- Health Check 실패 → 트래픽 차단
- Standby DB로 Failover (고가용성 구성)
- 복구 후 Outbox 메시지 재발행

## 8. 데이터 일관성 전략

### 8.1 Eventual Consistency
- 모든 서비스는 **최종적으로** 일관된 상태 도달
- 실시간 일관성이 필요한 부분은 동기 호출 (최소화)

### 8.2 Read Model (CQRS 패턴 - 선택적)
- 조회 전용 DB (Read Replica 또는 Elasticsearch)
- 이벤트를 구독하여 Read Model 업데이트
- 쓰기와 읽기 분리로 성능 향상

### 8.3 낙관적 락
- Version 필드를 통한 동시성 제어
- 충돌 시 재시도 로직

## 9. 모니터링 & 관찰성

### 9.1 분산 추적 (Zipkin)
- 모든 요청에 Trace ID 부여
- 서비스간 호출 체인 추적
- 병목 구간 식별

### 9.2 메트릭 (Prometheus + Grafana)
- **비즈니스 메트릭**:
  - 주문 생성 수 (per minute)
  - 결제 성공률
  - 정산 처리 건수
- **기술 메트릭**:
  - API 응답 시간 (P50, P95, P99)
  - Kafka Consumer Lag
  - DB Connection Pool 사용률
  - Outbox 미발행 건수

### 9.3 로깅
- 구조화된 로그 (JSON 포맷)
- Correlation ID 포함
- 중앙 집중식 로그 수집 (ELK Stack - 선택적)

### 9.4 알람
- **Critical**: DB 다운, Kafka 다운
- **Warning**: Consumer Lag > 1000, DLQ 메시지 누적
- **Info**: 정산 배치 완료

## 10. 보안

### 10.1 API 인증/인가
- JWT 기반 인증
- Spring Security 적용
- API Gateway (선택적)

### 10.2 데이터 암호화
- 민감 정보 (카드 번호) 암호화 저장
- TLS/SSL 통신

### 10.3 감사 로그
- 모든 중요 작업 (주문 생성, 결제, 정산) 감사 로그 기록

## 11. 테스트 전략

### 11.1 단위 테스트
- 비즈니스 로직 테스트
- Mock을 활용한 의존성 격리

### 11.2 통합 테스트
- Testcontainers (Kafka, PostgreSQL)
- Outbox 발행 → Kafka → Consumer 전체 플로우 테스트

### 11.3 계약 테스트 (Contract Test)
- 이벤트 스키마 검증
- Pact 또는 Spring Cloud Contract

### 11.4 카오스 엔지니어링
- 네트워크 지연 주입
- 서비스 강제 종료
- DB 장애 시뮬레이션

## 12. 배포 전략

### 12.1 Blue-Green Deployment
- 무중단 배포
- 빠른 롤백

### 12.2 Canary Deployment
- 일부 트래픽만 신규 버전으로 라우팅
- 점진적 배포

### 12.3 Database Migration
- Flyway 또는 Liquibase
- 하위 호환성 유지 (Expand-Contract 패턴)

## 13. 성능 목표

- **주문 생성 API**: P95 < 500ms
- **결제 승인**: P95 < 1000ms (외부 PG 포함)
- **정산 배치**: 100만 건 / 10분 이내
- **Kafka Consumer Lag**: < 100 메시지
- **가용성**: 99.9% (월 43분 다운타임 허용)

## 14. 기술 스택 요약

| 영역 | 기술 |
|------|------|
| 언어 | Java 25 |
| 프레임워크 | Spring Boot 4.0.1 |
| 메시징 | Apache Kafka 7.5.0 |
| 데이터베이스 | PostgreSQL 16 |
| 캐시 | Redis 7 |
| 추적 | Zipkin |
| 메트릭 | Prometheus + Grafana |
| 빌드 | Gradle |
| 컨테이너 | Docker, Docker Compose |
| ORM | Spring Data JPA (Hibernate) |

## 15. 다음 단계

1. ✅ 인프라 구축 (Docker Compose)
2. ⏳ Outbox 패턴 구현
3. ⏳ 각 서비스별 도메인 모델 설계
4. ⏳ Kafka Producer/Consumer 구현
5. ⏳ Saga 패턴 구현
6. ⏳ 멱등성, 재시도, DLQ 구현
7. ⏳ 모니터링, 알람 구현
8. ⏳ 통합 테스트 작성
