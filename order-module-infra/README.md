# Order-Settlement System Infrastructure

이 디렉토리는 주문-결제-정산 마이크로서비스를 위한 인프라 설정을 포함합니다.

## 포함된 서비스

### 메시징 & 이벤트 스트리밍
- **Kafka** (포트 9092, 9093): 메시지 브로커, Outbox 패턴의 이벤트 전달
- **Zookeeper** (포트 2181): Kafka 코디네이터
- **Kafka UI** (포트 8989): Kafka 토픽, 메시지 모니터링

### 데이터베이스
- **PostgreSQL (Order)** (포트 5432): 주문 서비스 DB
- **PostgreSQL (Payment)** (포트 5433): 결제 서비스 DB
- **PostgreSQL (Settlement)** (포트 5434): 정산 서비스 DB

### 캐싱 & 분산 락
- **Redis** (포트 6379): 캐싱, 분산 락, Idempotency Key 저장

### 모니터링 & 추적
- **Zipkin** (포트 9411): 분산 추적
- **Prometheus** (포트 9090): 메트릭 수집
- **Grafana** (포트 3000): 메트릭 시각화
- **Adminer** (포트 8080): DB 관리 UI

## 실행 방법

```bash
# 전체 인프라 시작
cd order-module-infra
docker-compose up -d

# 로그 확인
docker-compose logs -f

# 특정 서비스만 시작
docker-compose up -d kafka postgres-order redis

# 전체 중지
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

## 접속 정보

### Kafka
- Bootstrap Server (외부): `localhost:9093`
- Bootstrap Server (내부): `kafka:9092`
- Kafka UI: http://localhost:8989

### 데이터베이스
**Order DB:**
- Host: `localhost:5432`
- Database: `order_db`
- User: `order_user`
- Password: `order_password`

**Payment DB:**
- Host: `localhost:5433`
- Database: `payment_db`
- User: `payment_user`
- Password: `payment_password`

**Settlement DB:**
- Host: `localhost:5434`
- Database: `settlement_db`
- User: `settlement_user`
- Password: `settlement_password`

### Redis
- Host: `localhost:6379`
- Password: `redis_password`

### Monitoring
- Zipkin: http://localhost:9411
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- Adminer: http://localhost:8080

## Kafka 토픽

다음 토픽들이 자동으로 생성됩니다:

```
order.created         - 주문 생성 이벤트
order.cancelled       - 주문 취소 이벤트
payment.succeeded     - 결제 성공 이벤트
payment.failed        - 결제 실패 이벤트
settlement.completed  - 정산 완료 이벤트
saga.command          - Saga 오케스트레이션 커맨드
saga.reply            - Saga 응답 이벤트
dlq.order             - Order DLQ
dlq.payment           - Payment DLQ
dlq.settlement        - Settlement DLQ
```

## 헬스체크

모든 서비스의 헬스체크를 확인:

```bash
# PostgreSQL
docker exec postgres-order pg_isready -U order_user -d order_db
docker exec postgres-payment pg_isready -U payment_user -d payment_db
docker exec postgres-settlement pg_isready -U settlement_user -d settlement_db

# Redis
docker exec redis redis-cli -a redis_password ping

# Kafka
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

## 트러블슈팅

### Kafka 연결 실패
- Zookeeper가 먼저 완전히 시작되었는지 확인
- `docker-compose logs kafka` 로 로그 확인

### DB 연결 실패
- 헬스체크가 통과했는지 확인
- `docker-compose ps` 로 상태 확인

### 포트 충돌
- 이미 사용 중인 포트가 있다면 docker-compose.yml의 포트 매핑 수정
