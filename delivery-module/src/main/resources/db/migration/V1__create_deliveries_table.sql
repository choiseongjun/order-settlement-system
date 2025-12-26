-- 배송 테이블 생성
CREATE TABLE deliveries (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    address VARCHAR(200) NOT NULL,
    recipient_name VARCHAR(100),
    recipient_phone VARCHAR(20),
    tracking_number VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    saga_id VARCHAR(36),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_delivery_order_id ON deliveries(order_id);
CREATE INDEX idx_delivery_status ON deliveries(status);

COMMENT ON TABLE deliveries IS '배송 정보';
COMMENT ON COLUMN deliveries.status IS '배송 상태 (PENDING, CREATED, IN_TRANSIT, DELIVERED, CANCELLED)';
