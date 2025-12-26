-- 정산 대상 테이블 생성
CREATE TABLE settlement_targets (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    fee DECIMAL(19, 2),
    net_amount DECIMAL(19, 2),
    saga_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_settlement_order_id ON settlement_targets(order_id);
CREATE INDEX idx_settlement_created_at ON settlement_targets(created_at);

COMMENT ON TABLE settlement_targets IS '정산 대상';
COMMENT ON COLUMN settlement_targets.fee IS '수수료 (3%)';
COMMENT ON COLUMN settlement_targets.net_amount IS '정산 금액 (amount - fee)';
