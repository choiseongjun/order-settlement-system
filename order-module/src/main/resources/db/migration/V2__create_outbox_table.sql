-- Outbox 테이블 생성
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT
);

-- 인덱스 생성
CREATE INDEX idx_outbox_status_created_at ON outbox(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox(aggregate_type, aggregate_id);

-- 코멘트
COMMENT ON TABLE outbox IS 'Transactional Outbox 패턴용 테이블';
COMMENT ON COLUMN outbox.aggregate_type IS '집합 타입 (ORDER, PAYMENT 등)';
COMMENT ON COLUMN outbox.aggregate_id IS '집합 ID (주문 ID, 결제 ID 등)';
COMMENT ON COLUMN outbox.event_type IS '이벤트 타입';
COMMENT ON COLUMN outbox.payload IS '이벤트 페이로드 (JSON)';
COMMENT ON COLUMN outbox.status IS '발행 상태 (PENDING, PUBLISHED, FAILED)';
COMMENT ON COLUMN outbox.retry_count IS '재시도 횟수';
