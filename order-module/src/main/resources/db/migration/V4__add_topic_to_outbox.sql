-- Outbox 테이블에 topic 컬럼 추가
ALTER TABLE outbox ADD COLUMN topic VARCHAR(255);

-- 기존 데이터에 대한 topic 업데이트 (없을 수도 있지만 안전하게)
UPDATE outbox SET topic = 'unknown' WHERE topic IS NULL;

-- topic을 NOT NULL로 변경
ALTER TABLE outbox ALTER COLUMN topic SET NOT NULL;

-- 코멘트
COMMENT ON COLUMN outbox.topic IS 'Kafka 토픽 이름';
