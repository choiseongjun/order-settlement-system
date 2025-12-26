# 🚀 전체 시스템 실행 및 테스트 가이드

## 1️⃣ 인프라 실행

```bash
# 1. 인프라 디렉토리로 이동
cd order-module-infra

# 2. Docker Compose 실행
docker-compose up -d

# 3. 모든 컨테이너 상태 확인
docker-compose ps

# 4. 로그 확인 (선택사항)
docker-compose logs -f kafka
```

**확인 사항**:
- ✅ Kafka (포트 9092, 9093)
- ✅ PostgreSQL x4 (포트 5432, 5433, 5434, 5435)
- ✅ Redis (포트 6379)
- ✅ Zipkin (포트 9411)
- ✅ Prometheus (포트 9090)
- ✅ Grafana (포트 3000)
- ✅ Kafka UI (포트 8989)
- ✅ Adminer (포트 8080)

## 2️⃣ 서비스 실행 (4개 터미널)

### Terminal 1: Order Service (Orchestrator)
```bash
cd order-module
./gradlew bootRun

# 또는 Windows Gradle Wrapper
gradlew.bat bootRun
```
**포트**: 8081
**역할**: Saga Orchestrator

### Terminal 2: Payment Service
```bash
cd payment-module
./gradlew bootRun
```
**포트**: 8082
**역할**: Saga Participant (결제)

### Terminal 3: Delivery Service
```bash
cd delivery-module
./gradlew bootRun
```
**포트**: 8084
**역할**: Saga Participant (배송)

### Terminal 4: Settlement Service
```bash
cd settlement-module
./gradlew bootRun
```
**포트**: 8083
**역할**: Saga Participant (정산)

**모든 서비스가 시작될 때까지 대기 (~30초)**

## 3️⃣ 헬스 체크

```bash
# Order Service 확인
curl http://localhost:8081/actuator/health

# Payment Service 확인
curl http://localhost:8082/actuator/health

# Delivery Service 확인
curl http://localhost:8084/actuator/health

# Settlement Service 확인
curl http://localhost:8083/actuator/health
```

모든 응답이 `{"status":"UP"}`이면 정상입니다.

## 4️⃣ 주문 생성 테스트 (Saga 플로우)

### ✅ 테스트 1: 정상 주문 생성

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

**기대 응답**:
```json
{
  "id": 1,
  "userId": 1,
  "productId": 100,
  "quantity": 2,
  "totalAmount": 50000,
  "status": "PENDING",
  "sagaId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "deliveryAddress": "서울시 강남구 테헤란로 123",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "createdAt": "2025-12-26T...",
  "updatedAt": "2025-12-26T..."
}
```

**Saga 플로우 (80% 확률로 성공)**:
```
1. [Order Service] 주문 생성 (status: PENDING)
2. [Order → Payment] ApprovePaymentCommand 발행
3. [Payment Service] 결제 승인 (80% 성공률)
   ✅ 성공: PaymentApprovedReply 발행
   ❌ 실패: PaymentFailedReply 발행 → 주문 취소
4. [Order Service] Reply 수신 → status: PAYMENT_APPROVED
5. [Order → Delivery] CreateDeliveryCommand 발행
6. [Delivery Service] 배송 생성 (90% 성공률)
   ✅ 성공: DeliveryCreatedReply 발행
   ❌ 실패: DeliveryFailedReply 발행 → 결제 취소 → 주문 취소
7. [Order Service] Reply 수신 → status: CONFIRMED
8. [Order → Settlement] CreateSettlementCommand 발행
9. [Settlement Service] 정산 대상 추가
10. [Order Service] SettlementCreatedReply 수신 → Saga 완료!
```

**최종 상태 확인** (10초 후):
```bash
# 주문 조회
curl http://localhost:8081/api/orders/1
```

**성공 시**:
- `"status": "CONFIRMED"`

**실패 시**:
- `"status": "CANCELLED"`

### ✅ 테스트 2: 여러 주문 생성

```bash
# 주문 2
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 2,
    "productId": 200,
    "quantity": 1,
    "totalAmount": 30000,
    "deliveryAddress": "부산시 해운대구 센텀로 456",
    "recipientName": "김철수",
    "recipientPhone": "010-2345-6789"
  }'

# 주문 3
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 150,
    "quantity": 3,
    "totalAmount": 75000,
    "deliveryAddress": "서울시 송파구 올림픽로 789",
    "recipientName": "이영희",
    "recipientPhone": "010-3456-7890"
  }'
```

### ✅ 테스트 3: 주문 조회

```bash
# 특정 주문 조회
curl http://localhost:8081/api/orders/1

# 사용자별 주문 목록
curl http://localhost:8081/api/orders/user/1

# 모든 주문 조회
curl http://localhost:8081/api/orders
```

## 5️⃣ Kafka 메시지 모니터링

### Kafka UI에서 확인
http://localhost:8989

**Command Topics** (Orchestrator → Participants):
- `saga.command.payment.approve` - 결제 승인 요청
- `saga.command.payment.cancel` - 결제 취소 요청 (보상)
- `saga.command.delivery.create` - 배송 생성 요청
- `saga.command.delivery.cancel` - 배송 취소 요청 (보상)
- `saga.command.settlement.create` - 정산 생성 요청

**Reply Topics** (Participants → Orchestrator):
- `saga.reply.payment` - 결제 응답 (Approved/Failed/Cancelled)
- `saga.reply.delivery` - 배송 응답 (Created/Failed)
- `saga.reply.settlement` - 정산 응답 (Created)

**메시지 예시 확인**:
1. Kafka UI → Topics 선택
2. `saga.command.payment.approve` 클릭
3. Messages 탭에서 실제 메시지 확인

## 6️⃣ 데이터베이스 확인

### Adminer에서 확인
http://localhost:8080

**Order DB (localhost:5432)**:
- 계정: order_user / order_password
- 테이블:
  - `orders` - 주문 정보
  - `outbox` - Outbox 패턴 메시지
  - `saga_instance` - Saga 진행 상황

**Payment DB (localhost:5433)**:
- 계정: payment_user / payment_password
- 테이블:
  - `payments` - 결제 정보

**Delivery DB (localhost:5435)**:
- 계정: delivery_user / delivery_password
- 테이블:
  - `deliveries` - 배송 정보

**Settlement DB (localhost:5434)**:
- 계정: settlement_user / settlement_password
- 테이블:
  - `settlement_targets` - 정산 대상

### SQL 쿼리 예시

```sql
-- Order Service: Saga 진행 상황 확인
SELECT saga_id, status, current_step, created_at
FROM saga_instance
ORDER BY created_at DESC;

-- Order Service: Outbox 발행 상태
SELECT id, event_type, status, created_at, published_at
FROM outbox
ORDER BY created_at DESC;

-- Order Service: 주문 상태
SELECT id, user_id, total_amount, status, saga_id, created_at
FROM orders
ORDER BY created_at DESC;

-- Payment Service: 결제 내역
SELECT id, order_id, amount, status, pg_transaction_id, created_at
FROM payments
ORDER BY created_at DESC;

-- Delivery Service: 배송 정보
SELECT id, order_id, tracking_number, status, created_at
FROM deliveries
ORDER BY created_at DESC;

-- Settlement Service: 정산 대상
SELECT id, order_id, payment_id, amount, fee, net_amount, created_at
FROM settlement_targets
ORDER BY created_at DESC;
```

## 7️⃣ 분산 추적 (Zipkin)

### Zipkin에서 확인
http://localhost:9411

1. **Run Query** 클릭
2. 최근 요청 목록 확인
3. 특정 Trace 클릭하여 전체 플로우 시각화 확인

**확인 가능한 정보**:
- 주문 생성부터 정산까지 전체 플로우
- 각 서비스별 처리 시간
- Kafka 메시지 발행/수신 타이밍

## 8️⃣ 메트릭 모니터링

### Prometheus
http://localhost:9090

**쿼리 예시**:
```
# HTTP 요청 수
http_server_requests_seconds_count

# JVM 메모리 사용량
jvm_memory_used_bytes
```

### Grafana
http://localhost:3000
- 계정: admin / admin

## 9️⃣ 보상 트랜잭션 테스트

주문을 여러 번 생성하면 다음과 같은 시나리오가 발생합니다:

### 시나리오 1: 결제 실패 (20% 확률)
```
1. 주문 생성 (PENDING)
2. 결제 실패 → PaymentFailedReply
3. 주문 취소 (CANCELLED)
```

**확인**:
```bash
curl http://localhost:8081/api/orders
# status가 "CANCELLED"인 주문 확인
```

### 시나리오 2: 배송 실패 (10% 확률)
```
1. 주문 생성 (PENDING)
2. 결제 성공 (PAYMENT_APPROVED)
3. 배송 실패 → DeliveryFailedReply
4. 결제 취소 (보상 트랜잭션)
5. 주문 취소 (CANCELLED)
```

**Saga Instance 확인** (Adminer):
```sql
SELECT saga_id, status, current_step, compensation_data
FROM saga_instance
WHERE status = 'ABORTED';
```

## 🔟 Redis 멱등성 확인

```bash
# Redis CLI 접속
docker exec -it redis redis-cli -a redis_password

# Idempotency Key 확인
KEYS *

# 특정 키 조회
GET "PaymentApproved:saga-id-xxxx"
```

## 1️⃣1️⃣ 성능 테스트

### 연속 주문 생성 (100개)

```bash
# Linux/Mac
for i in {1..100}; do
  curl -X POST http://localhost:8081/api/orders \
    -H "Content-Type: application/json" \
    -d "{
      \"userId\": $((RANDOM % 10 + 1)),
      \"productId\": $((RANDOM % 500 + 100)),
      \"quantity\": $((RANDOM % 5 + 1)),
      \"totalAmount\": $((RANDOM % 100000 + 10000)),
      \"deliveryAddress\": \"서울시 강남구 테헤란로 $i\",
      \"recipientName\": \"테스트$i\",
      \"recipientPhone\": \"010-0000-$i\"
    }"
  sleep 0.1
done
```

### Windows PowerShell
```powershell
1..100 | ForEach-Object {
  $body = @{
    userId = Get-Random -Minimum 1 -Maximum 10
    productId = Get-Random -Minimum 100 -Maximum 500
    quantity = Get-Random -Minimum 1 -Maximum 5
    totalAmount = Get-Random -Minimum 10000 -Maximum 100000
    deliveryAddress = "서울시 강남구 테헤란로 $_"
    recipientName = "테스트$_"
    recipientPhone = "010-0000-$_"
  } | ConvertTo-Json

  Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/orders" `
    -ContentType "application/json" -Body $body
  Start-Sleep -Milliseconds 100
}
```

**확인**:
```bash
# 성공/실패 비율 확인
curl http://localhost:8081/api/orders | grep -c "CONFIRMED"
curl http://localhost:8081/api/orders | grep -c "CANCELLED"
```

## 1️⃣2️⃣ 트러블슈팅

### 문제 1: Kafka 연결 실패
```bash
# Kafka 상태 확인
docker-compose logs kafka

# Kafka 재시작
docker-compose restart kafka
```

### 문제 2: DB 연결 실패
```bash
# PostgreSQL 상태 확인
docker exec postgres-order pg_isready -U order_user -d order_db

# DB 재시작
docker-compose restart postgres-order
```

### 문제 3: Outbox가 발행되지 않음
- OutboxRelay가 동작하는지 확인
- Order Service 로그에서 "Relaying" 메시지 확인
- Outbox 테이블에서 PENDING 상태 메시지 확인

### 문제 4: Saga가 진행되지 않음
- saga_instance 테이블에서 현재 단계 확인
- Kafka UI에서 Consumer Lag 확인
- 각 서비스 로그 확인

## 1️⃣3️⃣ 시스템 종료

```bash
# 서비스 종료 (Ctrl+C in each terminal)

# 인프라 종료
cd order-module-infra
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

---

## 🎯 성공 기준

✅ **정상 플로우 (72% 확률)**:
- 주문: PENDING → PAYMENT_APPROVED → CONFIRMED
- 결제: APPROVED
- 배송: CREATED (tracking number 생성)
- 정산: 정산 대상 추가 (수수료 3% 차감)

✅ **결제 실패 플로우 (20% 확률)**:
- 주문: PENDING → CANCELLED
- 결제: FAILED

✅ **배송 실패 플로우 (8% 확률)**:
- 주문: PENDING → PAYMENT_APPROVED → CANCELLED
- 결제: APPROVED → CANCELLED (보상)
- 배송: CANCELLED

---

**모든 시스템이 정상 작동하면 7년차 레벨의 분산 시스템 구현 완료!** 🎉
