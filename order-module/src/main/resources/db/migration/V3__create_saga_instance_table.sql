-- Saga Instance 테이블 생성
CREATE TABLE saga_instance (
    saga_id VARCHAR(36) PRIMARY KEY,
    saga_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    compensation_data TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_saga_aggregate_id ON saga_instance(aggregate_id);
CREATE INDEX idx_saga_status ON saga_instance(status);

-- 코멘트
COMMENT ON TABLE saga_instance IS 'Saga Orchestration 패턴용 Saga 인스턴스';
COMMENT ON COLUMN saga_instance.saga_id IS 'Saga 고유 ID (UUID)';
COMMENT ON COLUMN saga_instance.saga_type IS 'Saga 타입 (ORDER_FULFILLMENT_SAGA)';
COMMENT ON COLUMN saga_instance.aggregate_id IS '집합 ID (주문 ID)';
COMMENT ON COLUMN saga_instance.status IS 'Saga 상태 (STARTED, COMPENSATING, COMPLETED, ABORTED)';
COMMENT ON COLUMN saga_instance.current_step IS '현재 단계';
COMMENT ON COLUMN saga_instance.payload IS 'Saga 컨텍스트 (JSON)';
COMMENT ON COLUMN saga_instance.compensation_data IS '보상 트랜잭션 데이터 (JSON)';
