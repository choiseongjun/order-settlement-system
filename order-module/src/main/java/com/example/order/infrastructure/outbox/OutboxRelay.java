package com.example.order.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outbox Relay - Outbox 테이블을 폴링하여 Kafka로 메시지 발행
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRY_COUNT = 3;

    @Scheduled(fixedDelay = 1000) // 1초마다 폴링
    @Transactional
    public void relayPendingMessages() {
        List<Outbox> pendingMessages = outboxRepository
            .findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, BATCH_SIZE)
            );

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.debug("Relaying {} outbox messages", pendingMessages.size());

        for (Outbox outbox : pendingMessages) {
            try {
                publishToKafka(outbox);
                outbox.markAsPublished();
                outboxRepository.save(outbox);

                log.info("Published: eventType={}, aggregateId={}",
                    outbox.getEventType(), outbox.getAggregateId());

            } catch (Exception e) {
                log.error("Failed to publish: eventType={}, aggregateId={}, error={}",
                    outbox.getEventType(), outbox.getAggregateId(), e.getMessage());

                outbox.incrementRetryCount();

                if (outbox.getRetryCount() >= MAX_RETRY_COUNT) {
                    outbox.markAsFailed(e.getMessage());
                    log.error("Outbox message failed after {} retries: id={}", MAX_RETRY_COUNT, outbox.getId());
                }

                outboxRepository.save(outbox);
            }
        }
    }

    private void publishToKafka(Outbox outbox) {
        String topic = getTopicName(outbox.getEventType());
        String key = String.valueOf(outbox.getAggregateId());

        kafkaTemplate.send(topic, key, outbox.getPayload())
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    throw new RuntimeException("Kafka send failed", ex);
                }
            });
    }

    private String getTopicName(String eventType) {
        // OrderCreated → order.created
        return eventType
            .replaceAll("([A-Z])", ".$1")
            .toLowerCase()
            .substring(1);
    }
}
