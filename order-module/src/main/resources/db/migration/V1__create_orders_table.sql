-- 주문 테이블 생성
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    total_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    saga_id VARCHAR(36),
    cancel_reason VARCHAR(500),
    delivery_address VARCHAR(200),
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(20),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_status ON orders(status);
CREATE INDEX idx_saga_id ON orders(saga_id);

-- 코멘트
COMMENT ON TABLE orders IS '주문 정보';
COMMENT ON COLUMN orders.user_id IS '사용자 ID';
COMMENT ON COLUMN orders.product_id IS '상품 ID';
COMMENT ON COLUMN orders.quantity IS '수량';
COMMENT ON COLUMN orders.total_amount IS '총 금액';
COMMENT ON COLUMN orders.status IS '주문 상태 (PENDING, PAYMENT_REQUESTED, PAYMENT_APPROVED, DELIVERY_REQUESTED, CONFIRMED, CANCELLED, FAILED)';
COMMENT ON COLUMN orders.saga_id IS 'Saga 인스턴스 ID';
COMMENT ON COLUMN orders.cancel_reason IS '취소 사유';
COMMENT ON COLUMN orders.version IS '낙관적 락 버전';
