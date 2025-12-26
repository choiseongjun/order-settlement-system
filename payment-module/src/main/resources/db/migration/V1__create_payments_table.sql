-- 결제 테이블 생성
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    payment_method VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    pg_transaction_id VARCHAR(100),
    saga_id VARCHAR(36),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_order_id ON payments(order_id);
CREATE INDEX idx_payment_status ON payments(status);

COMMENT ON TABLE payments IS '결제 정보';
COMMENT ON COLUMN payments.status IS '결제 상태 (PENDING, APPROVED, FAILED, CANCELLED)';
