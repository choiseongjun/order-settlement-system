# 주문-결제-정산 마이크로서비스 시스템

> **7년차 레벨의 엔터프라이즈급 분산 시스템**
>
> Transactional Outbox 패턴과 Saga 패턴을 활용한 최종 일관성(Eventual Consistency) 보장

## 프로젝트 개요

커머스 도메인에서 주문-결제-정산 플로우를 처리하는 마이크로서비스 아키텍처 기반 시스템입니다.
분산 트랜잭션, 장애 복구, 멱등성 보장 등 실전 프로덕션 환경에 필요한 모든 패턴을 구현합니다.

## 핵심 기술

- **언어/프레임워크**: Java 25 + Spring Boot 4.0.1
- **메시징**: Apache Kafka 7.5.0
- **데이터베이스**: PostgreSQL 16
- **캐시**: Redis 7
- **모니터링**: Prometheus + Grafana + Zipkin
- **패턴**: Outbox Pattern, Saga Pattern (Choreography)

## 시스템 아키텍처

### 서비스 구성

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  Order Service  │       │ Payment Service │       │Settlement Service│
│   (Port 8081)   │       │   (Port 8082)   │       │   (Port 8083)   │
│                 │       │                 │       │                 │
│  PostgreSQL     │       │  PostgreSQL     │       │  PostgreSQL     │
│  (Port 5432)    │       │  (Port 5433)    │       │  (Port 5434)    │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         └─────────────┬───────────┴─────────────┬───────────┘
                       │                         │
                  ┌────▼────┐              ┌─────▼─────┐
                  │  Kafka  │              │   Redis   │
                  │(9092/93)│              │  (6379)   │
                  └─────────┘              └───────────┘
```

### 주요 특징

✅ **분산 트랜잭션 관리** - Saga 패턴으로 비즈니스 트랜잭션 보장
✅ **최종 일관성** - Outbox 패턴으로 안정적인 이벤트 발행
✅ **멱등성 보장** - 중복 메시지 처리 방지 (Redis 기반)
✅ **장애 복구** - DLQ, 재시도, 보상 트랜잭션
✅ **관찰성** - 분산 추적, 메트릭, 구조화된 로깅

## 빠른 시작

### 1. 인프라 실행

```bash
cd order-module-infra
docker-compose up -d

# 모든 서비스가 정상 실행되었는지 확인
docker-compose ps
```

**실행되는 서비스**:
- Kafka + Zookeeper
- PostgreSQL (Order, Payment, Settlement)
- Redis
- Zipkin (분산 추적)
- Prometheus + Grafana (모니터링)
- Kafka UI (http://localhost:8989)
- Adminer (http://localhost:8080)

### 2. 애플리케이션 실행 (예정)

```bash
# Order Service
cd order-module
./gradlew bootRun

# Payment Service
cd payment-module
./gradlew bootRun

# Settlement Service
cd settlement-module
./gradlew bootRun
```

### 3. API 테스트

```bash
# 주문 생성
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 100,
    "quantity": 2,
    "totalAmount": 50000
  }'

# 주문 조회
curl http://localhost:8081/api/orders/{orderId}
```

## 모니터링 대시보드

| 서비스 | URL | 용도 |
|--------|-----|------|
| Kafka UI | http://localhost:8989 | Kafka 토픽, 메시지 모니터링 |
| Adminer | http://localhost:8080 | DB 관리 |
| Zipkin | http://localhost:9411 | 분산 추적 |
| Prometheus | http://localhost:9090 | 메트릭 수집 |
| Grafana | http://localhost:3000 | 메트릭 시각화 (admin/admin) |

## 프로젝트 구조

```
order-settlement-system/
├── order-module/              # 주문 서비스 (Port 8081)
├── payment-module/            # 결제 서비스 (Port 8082)
├── settlement-module/         # 정산 서비스 (Port 8083)
├── order-module-infra/        # Docker Compose 인프라
├── ARCHITECTURE.md            # 🔥 아키텍처 설계 문서
├── OUTBOX_PATTERN.md         # 🔥 Outbox 패턴 가이드
├── SAGA_PATTERN.md           # 🔥 Saga 패턴 가이드
├── PROJECT_STRUCTURE.md      # 🔥 프로젝트 구조 가이드
└── README.md                 # 이 파일
```

## 핵심 문서

반드시 읽어야 할 설계 문서:

1. **[ARCHITECTURE.md](./ARCHITECTURE.md)** - 전체 시스템 아키텍처, 서비스 구성, 통신 패턴
2. **[OUTBOX_PATTERN.md](./OUTBOX_PATTERN.md)** - Transactional Outbox 패턴 상세 구현
3. **[SAGA_PATTERN.md](./SAGA_PATTERN.md)** - Saga 패턴 (Choreography) 구현
4. **[PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)** - 프로젝트 구조 및 구현 우선순위

## 주요 플로우

### 주문 생성 플로우 (정상 시나리오)

```
1. [Client] → POST /api/orders
2. [Order Service] 주문 생성 (status: PENDING) + Outbox에 이벤트 저장
3. [Outbox Relay] Kafka로 OrderCreated 이벤트 발행
4. [Payment Service] OrderCreated 구독 → 결제 승인 시도
5. [Payment Service] PaymentSucceeded 이벤트 발행
6. [Order Service] PaymentSucceeded 구독 → 주문 확정 (status: CONFIRMED)
7. [Settlement Service] PaymentSucceeded 구독 → 정산 대상 추가
```

### 보상 트랜잭션 플로우 (결제 실패 시)

```
1. [Payment Service] 결제 승인 실패 → PaymentFailed 이벤트 발행
2. [Order Service] PaymentFailed 구독 → 주문 취소 (status: CANCELLED)
3. [Order Service] OrderCancelled 이벤트 발행
```

## Kafka 토픽

| 토픽 | 발행자 | 구독자 | 용도 |
|------|--------|--------|------|
| `order.created` | Order Service | Payment Service | 주문 생성 알림 |
| `order.confirmed` | Order Service | - | 주문 확정 알림 |
| `order.cancelled` | Order Service | Payment Service | 주문 취소 알림 |
| `payment.succeeded` | Payment Service | Order, Settlement | 결제 성공 알림 |
| `payment.failed` | Payment Service | Order Service | 결제 실패 알림 |
| `settlement.completed` | Settlement Service | - | 정산 완료 알림 |

## 개발 로드맵

### Phase 1: 기본 인프라 ✅
- [x] Docker Compose 설정
- [x] 아키텍처 설계 문서 작성
- [x] Outbox/Saga 패턴 가이드 작성

### Phase 2: Order Service 구현 (진행 예정)
- [ ] 도메인 모델 (Order, OrderStatus)
- [ ] JPA Repository 및 Flyway 마이그레이션
- [ ] Outbox 패턴 구현
- [ ] REST API (주문 생성/조회/취소)
- [ ] Kafka Producer (이벤트 발행)

### Phase 3: Payment Service 구현
- [ ] 도메인 모델 (Payment, PaymentStatus)
- [ ] Kafka Consumer (OrderCreated 구독)
- [ ] Mock PG 클라이언트 (결제 승인/취소)
- [ ] Outbox + Saga Instance 관리
- [ ] 멱등성 처리

### Phase 4: Order-Payment 통합
- [ ] PaymentSucceeded/Failed 이벤트 처리
- [ ] Saga 플로우 완성 (정상/보상)
- [ ] 통합 테스트 (Testcontainers)

### Phase 5: Settlement Service 구현
- [ ] 정산 도메인 모델
- [ ] PaymentSucceeded 구독
- [ ] Spring Batch (일 단위 정산 집계)
- [ ] 정산 리포트 API

### Phase 6: 모니터링 & 운영
- [ ] Prometheus 메트릭 추가
- [ ] Grafana 대시보드 구성
- [ ] DLQ 처리 및 재시도 로직
- [ ] Slack 알람 연동

## 테스트 전략

### 단위 테스트
- 비즈니스 로직 테스트 (JUnit 5 + Mockito)

### 통합 테스트
- Testcontainers (Kafka, PostgreSQL)
- Outbox 발행 → Kafka → Consumer 전체 플로우

### E2E 테스트
- 주문 생성 → 결제 → 정산 전체 시나리오
- 장애 시나리오 (결제 실패, 네트워크 장애)

## 장애 시나리오 대응

| 시나리오 | 대응 전략 |
|---------|----------|
| Kafka 브로커 다운 | Outbox에 메시지 누적, 복구 후 자동 발행 |
| Payment Service 다운 | Consumer Lag 증가, 복구 후 밀린 메시지 처리 |
| 결제 승인 실패 | PaymentFailed 이벤트 → 주문 취소 (보상 트랜잭션) |
| 중복 메시지 | Idempotency Key (Redis) 로 중복 처리 방지 |
| DB 장애 | Health Check 실패 → 트래픽 차단, Standby DB Failover |

## 성능 목표

- **주문 생성 API**: P95 < 500ms
- **결제 승인**: P95 < 1000ms (외부 PG 포함)
- **정산 배치**: 100만 건 / 10분 이내
- **Kafka Consumer Lag**: < 100 메시지
- **가용성**: 99.9% (월 43분 다운타임 허용)

## 기여하기

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 라이선스

This project is licensed under the MIT License.

## 문의

프로젝트에 대한 질문이나 제안사항이 있으시면 Issue를 등록해주세요.

---

**Built with ❤️ using Java, Spring Boot, Kafka, and Microservices**
