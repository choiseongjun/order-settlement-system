# 📡 API 호출 플로우 가이드

## 전체 플로우 개요

```
사용자 → Order API → Saga Orchestration → Payment → Delivery → Settlement → 완료
```

---

## 1️⃣ 주문 생성 (Order Creation)

### API 엔드포인트
```http
POST http://localhost:8081/api/orders
Content-Type: application/json
```

### Request Body
```json
{
  "userId": 1,
  "productId": 100,
  "quantity": 2,
  "totalAmount": 50000,
  "deliveryAddress": "서울시 강남구 테헤란로 123",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678"
}
```

### Response (즉시 반환)
```json
{
  "id": 1,
  "userId": 1,
  "productId": 100,
  "quantity": 2,
  "totalAmount": 50000,
  "status": "PENDING",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "deliveryAddress": "서울시 강남구 테헤란로 123",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "createdAt": "2025-12-26T10:30:00",
  "updatedAt": "2025-12-26T10:30:00"
}
```

### curl 예시
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

### 처리 내용
1. ✅ 주문 생성 (status: PENDING)
2. ✅ Saga 인스턴스 생성
3. ✅ OrderCreated 이벤트를 Outbox에 저장
4. ✅ 즉시 응답 반환

---

## 2️⃣ 결제 승인 (Payment Approval)

### 내부 처리 (Saga Orchestration)

**Order Service → Payment Service**

#### Command 발행 (Kafka)
```
Topic: saga.command.payment.approve
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "userId": 1,
  "amount": 50000,
  "paymentMethod": "CREDIT_CARD"
}
```

#### Payment Service 처리
1. Mock PG 결제 처리 (80% 성공률)
2. Payment 엔티티 생성
3. Redis 멱등성 체크

#### Reply 발행 (Kafka)

**성공 시 (80% 확률):**
```
Topic: saga.reply.payment
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "paymentId": 1,
  "status": "APPROVED",
  "pgTransactionId": "PG-1234567890",
  "message": "Payment approved successfully"
}
```

**실패 시 (20% 확률):**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "status": "FAILED",
  "message": "Insufficient funds"
}
```

### 주문 상태 확인 API
```bash
# 결제 승인 후 주문 상태 확인
curl http://localhost:8081/api/orders/1
```

**Response (결제 성공 시):**
```json
{
  "id": 1,
  "status": "PAYMENT_APPROVED",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

**Response (결제 실패 시):**
```json
{
  "id": 1,
  "status": "CANCELLED",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

---

## 3️⃣ 배송 생성 (Delivery Creation)

### 내부 처리 (결제 성공 시에만 진행)

**Order Service → Delivery Service**

#### Command 발행 (Kafka)
```
Topic: saga.command.delivery.create
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "userId": 1,
  "address": "서울시 강남구 테헤란로 123",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678"
}
```

#### Delivery Service 처리
1. Mock 배송 생성 (90% 성공률)
2. Delivery 엔티티 생성
3. Tracking Number 자동 생성
4. Redis 멱등성 체크

#### Reply 발행 (Kafka)

**성공 시 (90% 확률):**
```
Topic: saga.reply.delivery
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "deliveryId": 1,
  "status": "CREATED",
  "trackingNumber": "TRK-1735189800123",
  "message": "Delivery created successfully"
}
```

**실패 시 (10% 확률):**
```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "status": "FAILED",
  "message": "Delivery service unavailable"
}
```

### 주문 상태 확인 API
```bash
# 배송 생성 후 주문 상태 확인
curl http://localhost:8081/api/orders/1
```

**Response (배송 성공 시):**
```json
{
  "id": 1,
  "status": "CONFIRMED",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

**Response (배송 실패 시 - 보상 트랜잭션 실행):**
```json
{
  "id": 1,
  "status": "CANCELLED",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

---

## 4️⃣ 정산 추가 (Settlement Creation)

### 내부 처리 (배송 성공 시에만 진행)

**Order Service → Settlement Service**

#### Command 발행 (Kafka)
```
Topic: saga.command.settlement.create
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "paymentId": 1,
  "amount": 50000
}
```

#### Settlement Service 처리
1. 정산 대상 생성
2. 수수료 계산 (3%)
   - amount: 50,000원
   - fee: 1,500원 (3%)
   - netAmount: 48,500원
3. Redis 멱등성 체크

#### Reply 발행 (Kafka)
```
Topic: saga.reply.settlement
```

```json
{
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "orderId": 1,
  "settlementId": 1,
  "status": "CREATED",
  "amount": 50000,
  "fee": 1500,
  "netAmount": 48500,
  "message": "Settlement created successfully"
}
```

### 최종 주문 상태 확인 API
```bash
# 정산 완료 후 최종 주문 상태 확인
curl http://localhost:8081/api/orders/1
```

**Response (전체 성공 - 72% 확률):**
```json
{
  "id": 1,
  "userId": 1,
  "productId": 100,
  "quantity": 2,
  "totalAmount": 50000,
  "status": "CONFIRMED",
  "sagaId": "550e8400-e29b-41d4-a716-446655440000",
  "deliveryAddress": "서울시 강남구 테헤란로 123",
  "recipientName": "홍길동",
  "recipientPhone": "010-1234-5678",
  "createdAt": "2025-12-26T10:30:00",
  "updatedAt": "2025-12-26T10:30:15"
}
```

---

## 5️⃣ 전체 API 목록

### Order Service (8081)

#### 1. 주문 생성
```bash
POST /api/orders
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{...}'
```

#### 2. 주문 조회 (ID)
```bash
GET /api/orders/{orderId}
curl http://localhost:8081/api/orders/1
```

#### 3. 사용자별 주문 목록
```bash
GET /api/orders/user/{userId}
curl http://localhost:8081/api/orders/user/1
```

#### 4. 전체 주문 목록
```bash
GET /api/orders
curl http://localhost:8081/api/orders
```

#### 5. 헬스 체크
```bash
GET /actuator/health
curl http://localhost:8081/actuator/health
```

---

### Payment Service (8082)

#### 1. 헬스 체크
```bash
GET /actuator/health
curl http://localhost:8082/actuator/health
```

**참고:** Payment Service는 REST API가 없고 Kafka Command만 수신합니다.

---

### Delivery Service (8084)

#### 1. 헬스 체크
```bash
GET /actuator/health
curl http://localhost:8084/actuator/health
```

**참고:** Delivery Service는 REST API가 없고 Kafka Command만 수신합니다.

---

### Settlement Service (8083)

#### 1. 헬스 체크
```bash
GET /actuator/health
curl http://localhost:8083/actuator/health
```

**참고:** Settlement Service는 REST API가 없고 Kafka Command만 수신합니다.

---

## 6️⃣ 보상 트랜잭션 (Compensation)

### 시나리오 1: 결제 실패 (20% 확률)

```
1. POST /api/orders → 주문 생성 (PENDING)
2. ApprovePaymentCommand → 결제 실패 ❌
3. PaymentFailedReply → Order Service 수신
4. 주문 취소 (CANCELLED)
```

**확인:**
```bash
curl http://localhost:8081/api/orders/1
# Response: {"status": "CANCELLED"}
```

---

### 시나리오 2: 배송 실패 (8% 확률)

```
1. POST /api/orders → 주문 생성 (PENDING)
2. ApprovePaymentCommand → 결제 성공 ✅ (PAYMENT_APPROVED)
3. CreateDeliveryCommand → 배송 실패 ❌
4. DeliveryFailedReply → Order Service 수신
5. CancelPaymentCommand → 결제 취소 (보상 트랜잭션)
6. PaymentCancelledReply → Order Service 수신
7. 주문 취소 (CANCELLED)
```

**확인:**
```bash
curl http://localhost:8081/api/orders/1
# Response: {"status": "CANCELLED"}
```

---

## 7️⃣ 타이밍 가이드

| 단계 | 소요 시간 | 누적 시간 |
|------|----------|----------|
| 주문 생성 | 즉시 (~100ms) | 0.1초 |
| Outbox Relay | 최대 1초 | 1.1초 |
| 결제 처리 | ~2초 | 3.1초 |
| 배송 생성 | ~2초 | 5.1초 |
| 정산 추가 | ~2초 | 7.1초 |
| **전체 완료** | **~7-10초** | **10초** |

**권장 대기 시간:** 주문 생성 후 **10초 대기** 후 상태 확인

---

## 8️⃣ 전체 플로우 테스트

### 완전 자동화 스크립트

```bash
#!/bin/bash

# 1. 주문 생성
echo "1️⃣ 주문 생성..."
ORDER=$(curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "productId": 100,
    "quantity": 2,
    "totalAmount": 50000,
    "deliveryAddress": "서울시 강남구 테헤란로 123",
    "recipientName": "홍길동",
    "recipientPhone": "010-1234-5678"
  }')

ORDER_ID=$(echo $ORDER | jq -r '.id')
SAGA_ID=$(echo $ORDER | jq -r '.sagaId')
echo "주문 생성 완료: ID=$ORDER_ID, SagaID=$SAGA_ID"
echo "초기 상태: PENDING"

# 2. 2초 후 - 결제 진행 중
echo -e "\n2️⃣ 결제 처리 중... (2초 대기)"
sleep 2
STATUS=$(curl -s http://localhost:8081/api/orders/$ORDER_ID | jq -r '.status')
echo "현재 상태: $STATUS"

# 3. 5초 후 - 배송 진행 중
echo -e "\n3️⃣ 배송 생성 중... (3초 추가 대기)"
sleep 3
STATUS=$(curl -s http://localhost:8081/api/orders/$ORDER_ID | jq -r '.status')
echo "현재 상태: $STATUS"

# 4. 10초 후 - 정산 완료
echo -e "\n4️⃣ 정산 처리 중... (5초 추가 대기)"
sleep 5
FINAL=$(curl -s http://localhost:8081/api/orders/$ORDER_ID)
FINAL_STATUS=$(echo $FINAL | jq -r '.status')
echo "최종 상태: $FINAL_STATUS"

# 5. 결과 출력
echo -e "\n========================================="
if [ "$FINAL_STATUS" == "CONFIRMED" ]; then
  echo "✅ Saga 성공!"
  echo "주문 → 결제 → 배송 → 정산 완료"
elif [ "$FINAL_STATUS" == "CANCELLED" ]; then
  echo "❌ Saga 실패!"
  echo "보상 트랜잭션 실행됨"
fi
echo "========================================="
echo $FINAL | jq .
```

---

## 9️⃣ 성공률 통계

| 결과 | 확률 | 상태 | 설명 |
|------|------|------|------|
| **전체 성공** | 72% | CONFIRMED | 결제(80%) × 배송(90%) |
| **결제 실패** | 20% | CANCELLED | 결제 단계 실패 |
| **배송 실패** | 8% | CANCELLED | 배송 실패 + 결제 취소 |

**100개 주문 테스트 예상 결과:**
- ✅ 성공: 72개 (CONFIRMED)
- ❌ 실패: 28개 (CANCELLED)
  - 결제 실패: 20개
  - 배송 실패: 8개

---

## 🔟 디버깅 및 모니터링

### 1. Kafka 메시지 확인
**Kafka UI:** http://localhost:8989

Topics:
- `saga.command.payment.approve`
- `saga.command.payment.cancel`
- `saga.command.delivery.create`
- `saga.command.settlement.create`
- `saga.reply.payment`
- `saga.reply.delivery`
- `saga.reply.settlement`

### 2. 데이터베이스 확인
**Adminer:** http://localhost:8080

```sql
-- Order Service: Saga 진행 상황
SELECT saga_id, status, current_step, created_at
FROM saga_instance
WHERE saga_id = '550e8400-e29b-41d4-a716-446655440000';

-- Payment Service: 결제 내역
SELECT * FROM payments WHERE order_id = 1;

-- Delivery Service: 배송 정보
SELECT * FROM deliveries WHERE order_id = 1;

-- Settlement Service: 정산 내역
SELECT * FROM settlement_targets WHERE order_id = 1;
```

### 3. 분산 추적
**Zipkin:** http://localhost:9411

전체 Saga 플로우를 시각화하여 각 단계별 소요 시간 확인 가능

---

## 요약

### 사용자가 직접 호출하는 API
✅ **POST /api/orders** - 주문 생성 (단 1번의 호출)

### 자동으로 실행되는 Saga 플로우
```
1. Order Service → ApprovePaymentCommand → Payment Service
2. Payment Service → PaymentApprovedReply → Order Service
3. Order Service → CreateDeliveryCommand → Delivery Service
4. Delivery Service → DeliveryCreatedReply → Order Service
5. Order Service → CreateSettlementCommand → Settlement Service
6. Settlement Service → SettlementCreatedReply → Order Service
7. Saga 완료! (status: CONFIRMED)
```

### 상태 확인 API
✅ **GET /api/orders/{orderId}** - 주문 상태 조회 (언제든지 호출 가능)

**모든 것이 Saga Orchestration으로 자동 처리됩니다!** 🚀
