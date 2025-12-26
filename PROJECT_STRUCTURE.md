# 프로젝트 구조 가이드

## 1. 전체 프로젝트 구조

```
order-settlement-system/
├── order-module/                   # 주문 서비스
├── payment-module/                 # 결제 서비스
├── settlement-module/              # 정산 서비스
├── order-module-infra/             # 인프라 설정 (Docker Compose)
├── settings.gradle                 # Gradle 멀티 프로젝트 설정
├── ARCHITECTURE.md                 # 아키텍처 설계 문서
├── OUTBOX_PATTERN.md              # Outbox 패턴 가이드
├── SAGA_PATTERN.md                # Saga 패턴 가이드
└── README.md                      # 프로젝트 개요
```

## 2. 각 서비스 공통 구조

각 마이크로서비스는 헥사고날 아키텍처(Hexagonal Architecture) 기반의 계층형 구조를 따릅니다.

```
service-module/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/{service}/
│   │   │       ├── domain/                      # 도메인 계층
│   │   │       │   ├── model/                   # 엔티티, VO
│   │   │       │   ├── repository/              # Repository 인터페이스
│   │   │       │   ├── service/                 # 도메인 서비스
│   │   │       │   └── event/                   # 도메인 이벤트
│   │   │       │
│   │   │       ├── application/                 # 애플리케이션 계층
│   │   │       │   ├── service/                 # 유스케이스
│   │   │       │   ├── dto/                     # DTO
│   │   │       │   └── port/                    # 포트 인터페이스
│   │   │       │
│   │   │       ├── adapter/                     # 어댑터 계층
│   │   │       │   ├── in/                      # Inbound Adapter
│   │   │       │   │   ├── web/                 # REST Controller
│   │   │       │   │   └── event/               # Kafka Consumer
│   │   │       │   │
│   │   │       │   └── out/                     # Outbound Adapter
│   │   │       │       ├── persistence/         # JPA Repository 구현
│   │   │       │       ├── event/               # Kafka Producer
│   │   │       │       ├── external/            # 외부 API 클라이언트
│   │   │       │       └── cache/               # Redis
│   │   │       │
│   │   │       ├── infrastructure/              # 인프라 계층
│   │   │       │   ├── config/                  # 설정 클래스
│   │   │       │   ├── outbox/                  # Outbox 패턴 구현
│   │   │       │   ├── saga/                    # Saga 패턴 구현
│   │   │       │   ├── idempotency/             # 멱등성 처리
│   │   │       │   └── monitoring/              # 메트릭, 추적
│   │   │       │
│   │   │       └── common/                      # 공통 유틸리티
│   │   │           ├── exception/               # 예외 클래스
│   │   │           ├── util/                    # 유틸리티
│   │   │           └── constant/                # 상수
│   │   │
│   │   └── resources/
│   │       ├── application.yml                  # 기본 설정
│   │       ├── application-local.yml            # 로컬 환경
│   │       ├── application-dev.yml              # 개발 환경
│   │       ├── application-prod.yml             # 운영 환경
│   │       ├── db/
│   │       │   └── migration/                   # Flyway 마이그레이션
│   │       │       ├── V1__init_schema.sql
│   │       │       ├── V2__add_outbox_table.sql
│   │       │       └── V3__add_saga_table.sql
│   │       └── logback-spring.xml               # 로깅 설정
│   │
│   └── test/
│       └── java/
│           └── com/example/{service}/
│               ├── unit/                        # 단위 테스트
│               ├── integration/                 # 통합 테스트
│               └── e2e/                         # E2E 테스트
│
├── build.gradle                                 # Gradle 빌드 스크립트
├── Dockerfile                                   # Docker 이미지 빌드
└── README.md                                    # 서비스 문서
```

## 3. Order Service 상세 구조

```
order-module/
├── src/main/java/com/example/order/
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java                      # 주문 엔티티
│   │   │   ├── OrderStatus.java                # 주문 상태 (Enum)
│   │   │   └── OrderLine.java                  # 주문 항목 (선택적)
│   │   │
│   │   ├── repository/
│   │   │   └── OrderRepository.java            # 주문 Repository 인터페이스
│   │   │
│   │   ├── service/
│   │   │   └── OrderDomainService.java         # 주문 도메인 로직
│   │   │
│   │   └── event/
│   │       ├── OrderCreatedEvent.java          # 주문 생성 이벤트
│   │       ├── OrderConfirmedEvent.java        # 주문 확정 이벤트
│   │       └── OrderCancelledEvent.java        # 주문 취소 이벤트
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── OrderService.java               # 주문 생성/조회/취소 유스케이스
│   │   │   └── OrderEventHandler.java          # 외부 이벤트 처리
│   │   │
│   │   ├── dto/
│   │   │   ├── OrderRequest.java               # 주문 생성 요청
│   │   │   ├── OrderResponse.java              # 주문 응답
│   │   │   └── OrderDetailResponse.java        # 주문 상세 응답
│   │   │
│   │   └── port/
│   │       ├── in/
│   │       │   └── OrderUseCase.java           # 주문 유스케이스 인터페이스
│   │       └── out/
│   │           ├── LoadOrderPort.java          # 주문 조회 포트
│   │           └── SaveOrderPort.java          # 주문 저장 포트
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── web/
│   │   │   │   ├── OrderController.java        # REST API
│   │   │   │   └── OrderQueryController.java   # 조회 API
│   │   │   │
│   │   │   └── event/
│   │   │       ├── PaymentEventConsumer.java   # 결제 이벤트 수신
│   │   │       └── KafkaConsumerConfig.java    # Kafka Consumer 설정
│   │   │
│   │   └── out/
│   │       ├── persistence/
│   │       │   ├── OrderJpaRepository.java     # JPA Repository 구현
│   │       │   └── OrderPersistenceAdapter.java # 영속성 어댑터
│   │       │
│   │       └── event/
│   │           ├── OrderEventProducer.java     # Kafka Producer
│   │           └── KafkaProducerConfig.java    # Kafka Producer 설정
│   │
│   ├── infrastructure/
│   │   ├── config/
│   │   │   ├── JpaConfig.java                  # JPA 설정
│   │   │   ├── RedisConfig.java                # Redis 설정
│   │   │   ├── KafkaConfig.java                # Kafka 공통 설정
│   │   │   └── SecurityConfig.java             # 보안 설정
│   │   │
│   │   ├── outbox/
│   │   │   ├── Outbox.java                     # Outbox 엔티티
│   │   │   ├── OutboxRepository.java           # Outbox Repository
│   │   │   ├── OutboxRelay.java                # Outbox 폴링/발행
│   │   │   └── OutboxCleanupJob.java           # Outbox 정리 배치
│   │   │
│   │   ├── saga/
│   │   │   ├── SagaInstance.java               # Saga 인스턴스 엔티티
│   │   │   ├── SagaInstanceRepository.java     # Saga Repository
│   │   │   ├── SagaType.java                   # Saga 타입 (Enum)
│   │   │   ├── SagaStatus.java                 # Saga 상태 (Enum)
│   │   │   └── SagaStep.java                   # Saga 단계 (Enum)
│   │   │
│   │   ├── idempotency/
│   │   │   ├── IdempotencyService.java         # 멱등성 서비스
│   │   │   └── IdempotencyKey.java             # 멱등성 키 생성
│   │   │
│   │   └── monitoring/
│   │       ├── OrderMetrics.java               # 커스텀 메트릭
│   │       └── TracingConfig.java              # 분산 추적 설정
│   │
│   └── common/
│       ├── exception/
│       │   ├── OrderNotFoundException.java     # 주문 미발견 예외
│       │   ├── InvalidOrderStateException.java # 잘못된 상태 예외
│       │   └── GlobalExceptionHandler.java     # 전역 예외 핸들러
│       │
│       ├── util/
│       │   ├── JsonUtil.java                   # JSON 변환
│       │   └── DateUtil.java                   # 날짜 유틸리티
│       │
│       └── constant/
│           └── KafkaTopics.java                # Kafka 토픽 상수
│
└── resources/
    ├── application.yml
    ├── db/migration/
    │   ├── V1__create_order_table.sql
    │   ├── V2__create_outbox_table.sql
    │   └── V3__create_saga_instance_table.sql
    └── logback-spring.xml
```

## 4. Payment Service 상세 구조

```
payment-module/
├── src/main/java/com/example/payment/
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Payment.java                    # 결제 엔티티
│   │   │   ├── PaymentStatus.java              # 결제 상태 (Enum)
│   │   │   └── PaymentMethod.java              # 결제 수단 (Enum)
│   │   │
│   │   ├── repository/
│   │   │   └── PaymentRepository.java
│   │   │
│   │   └── event/
│   │       ├── PaymentSucceededEvent.java
│   │       ├── PaymentFailedEvent.java
│   │       └── PaymentCancelledEvent.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── PaymentService.java             # 결제 승인/취소
│   │   │   └── PaymentEventHandler.java        # 주문 이벤트 처리
│   │   │
│   │   └── dto/
│   │       ├── PaymentRequest.java
│   │       ├── PaymentResponse.java
│   │       └── PaymentDetailResponse.java
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── web/
│   │   │   │   └── PaymentController.java
│   │   │   │
│   │   │   └── event/
│   │   │       └── OrderEventConsumer.java     # 주문 이벤트 수신
│   │   │
│   │   └── out/
│   │       ├── persistence/
│   │       │   └── PaymentJpaRepository.java
│   │       │
│   │       ├── event/
│   │       │   └── PaymentEventProducer.java
│   │       │
│   │       └── external/
│   │           ├── PgClient.java               # PG사 연동 클라이언트
│   │           └── MockPgClient.java           # Mock PG 클라이언트
│   │
│   ├── infrastructure/
│   │   ├── config/
│   │   │   ├── JpaConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   ├── KafkaConfig.java
│   │   │   └── PgClientConfig.java             # PG 클라이언트 설정
│   │   │
│   │   ├── outbox/
│   │   │   ├── Outbox.java
│   │   │   ├── OutboxRepository.java
│   │   │   └── OutboxRelay.java
│   │   │
│   │   ├── saga/
│   │   │   ├── SagaInstance.java
│   │   │   └── SagaInstanceRepository.java
│   │   │
│   │   └── idempotency/
│   │       └── IdempotencyService.java
│   │
│   └── common/
│       ├── exception/
│       │   ├── PaymentException.java           # 결제 실패 예외
│       │   └── DuplicatePaymentException.java  # 중복 결제 예외
│       │
│       └── constant/
│           └── KafkaTopics.java
│
└── resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_payment_table.sql
        ├── V2__create_outbox_table.sql
        └── V3__create_saga_instance_table.sql
```

## 5. Settlement Service 상세 구조

```
settlement-module/
├── src/main/java/com/example/settlement/
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Settlement.java                 # 정산 엔티티
│   │   │   ├── SettlementDetail.java           # 정산 상세
│   │   │   └── SettlementStatus.java           # 정산 상태 (Enum)
│   │   │
│   │   ├── repository/
│   │   │   ├── SettlementRepository.java
│   │   │   └── SettlementDetailRepository.java
│   │   │
│   │   └── event/
│   │       └── SettlementCompletedEvent.java
│   │
│   ├── application/
│   │   ├── service/
│   │   │   ├── SettlementService.java          # 정산 집계
│   │   │   ├── SettlementBatchService.java     # 배치 처리
│   │   │   └── SettlementEventHandler.java     # 결제 이벤트 처리
│   │   │
│   │   └── dto/
│   │       ├── SettlementRequest.java
│   │       ├── SettlementResponse.java
│   │       └── SettlementReportDto.java        # 리포트
│   │
│   ├── adapter/
│   │   ├── in/
│   │   │   ├── web/
│   │   │   │   ├── SettlementController.java
│   │   │   │   └── SettlementReportController.java # 리포트 API
│   │   │   │
│   │   │   ├── event/
│   │   │   │   └── PaymentEventConsumer.java   # 결제 이벤트 수신
│   │   │   │
│   │   │   └── batch/
│   │   │       └── SettlementBatchJob.java     # Spring Batch Job
│   │   │
│   │   └── out/
│   │       ├── persistence/
│   │       │   └── SettlementJpaRepository.java
│   │       │
│   │       └── event/
│   │           └── SettlementEventProducer.java
│   │
│   ├── infrastructure/
│   │   ├── config/
│   │   │   ├── JpaConfig.java
│   │   │   ├── BatchConfig.java                # Spring Batch 설정
│   │   │   └── KafkaConfig.java
│   │   │
│   │   └── outbox/
│   │       ├── Outbox.java
│   │       ├── OutboxRepository.java
│   │       └── OutboxRelay.java
│   │
│   └── common/
│       └── exception/
│           └── SettlementException.java
│
└── resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_settlement_table.sql
        ├── V2__create_settlement_detail_table.sql
        └── V3__create_outbox_table.sql
```

## 6. 공통 라이브러리 (선택적)

대규모 프로젝트에서는 공통 코드를 별도 모듈로 분리할 수 있습니다.

```
common-module/
├── src/main/java/com/example/common/
│   ├── outbox/
│   │   ├── Outbox.java                         # 공통 Outbox 엔티티
│   │   ├── OutboxRepository.java
│   │   └── OutboxRelay.java
│   │
│   ├── saga/
│   │   ├── SagaInstance.java
│   │   └── SagaInstanceRepository.java
│   │
│   ├── idempotency/
│   │   └── IdempotencyService.java
│   │
│   ├── event/
│   │   └── DomainEvent.java                    # 이벤트 베이스 클래스
│   │
│   └── util/
│       ├── JsonUtil.java
│       └── DateUtil.java
│
└── build.gradle
```

**Trade-off**:
- **장점**: 코드 재사용, 일관성 유지
- **단점**: 서비스 간 결합도 증가, 공통 모듈 변경 시 전체 재배포

**권장**: 초기에는 각 서비스에 복사, 패턴이 안정화되면 공통 모듈로 추출

## 7. 설정 파일 예시

### 7.1 application.yml (Order Service)

```yaml
spring:
  application:
    name: order-service
  profiles:
    active: local

  datasource:
    url: jdbc:postgresql://localhost:5432/order_db
    username: order_user
    password: order_password
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        show_sql: false
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  kafka:
    bootstrap-servers: localhost:9093
    consumer:
      group-id: order-service
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      retries: 3
      enable-idempotence: true

  data:
    redis:
      host: localhost
      port: 6379
      password: redis_password
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    com.example.order: DEBUG
    org.springframework.kafka: INFO
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

server:
  port: 8081
```

### 7.2 build.gradle (Order Service)

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.1'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
description = 'order-service'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Kafka
    implementation 'org.springframework.kafka:spring-kafka'

    // Redis
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'

    // Database
    runtimeOnly 'org.postgresql:postgresql'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'

    // Monitoring
    implementation 'io.micrometer:micrometer-registry-prometheus'
    implementation 'io.micrometer:micrometer-tracing-bridge-brave'
    implementation 'io.zipkin.reporter2:zipkin-reporter-brave'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Jackson
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'

    // Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:kafka'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

dependencyManagement {
    imports {
        mavenBom "org.testcontainers:testcontainers-bom:1.19.0"
    }
}

tasks.named('test') {
    useJUnitPlatform()
}
```

## 8. 구현 우선순위

### Phase 1: 기본 인프라 (완료)
- ✅ Docker Compose 설정
- ✅ 아키텍처 설계 문서

### Phase 2: Order Service 구현
1. 도메인 모델 (Order, OrderStatus)
2. JPA Repository
3. Outbox 패턴 구현
4. REST API (주문 생성/조회)
5. Kafka Producer (OrderCreated 발행)

### Phase 3: Payment Service 구현
1. 도메인 모델 (Payment, PaymentStatus)
2. Kafka Consumer (OrderCreated 구독)
3. Mock PG 클라이언트
4. Outbox + PaymentSucceeded/Failed 발행
5. Saga Instance 관리

### Phase 4: Order-Payment 통합
1. Order Service: PaymentSucceeded/Failed 구독
2. Saga 플로우 테스트 (정상/보상)
3. 멱등성 처리 (Redis)
4. 통합 테스트 (Testcontainers)

### Phase 5: Settlement Service 구현
1. 도메인 모델 (Settlement, SettlementDetail)
2. PaymentSucceeded 구독 → 정산 대상 추가
3. Spring Batch로 일 단위 정산 집계
4. 정산 리포트 API

### Phase 6: 모니터링 & 운영
1. Prometheus 메트릭 추가
2. Grafana 대시보드 구성
3. DLQ 처리 로직
4. 알람 시스템 (Slack 연동)

## 9. 다음 단계

1. ✅ 인프라 구축 완료
2. ⏳ Order Service 도메인 모델 구현
3. ⏳ Outbox 패턴 구현
4. ⏳ REST API 구현
5. ⏳ Kafka Producer/Consumer 구현

프로젝트 구조가 준비되었습니다. 이제 Order Service부터 단계적으로 구현을 시작하세요!
