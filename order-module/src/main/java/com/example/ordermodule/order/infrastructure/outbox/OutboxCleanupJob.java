package com.example.ordermodule.order.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Outbox Cleanup Job - 오래된 발행 완료 메시지 정리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupJob {

    private final OutboxRepository outboxRepository;

    @Scheduled(cron = "0 0 2 * * ?") // 매일 새벽 2시
    @Transactional
    public void cleanupOldOutboxMessages() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        int deleted = outboxRepository.deleteByStatusAndPublishedAtBefore(
            OutboxStatus.PUBLISHED, cutoff
        );

        log.info("Cleaned up {} old outbox messages", deleted);
    }
}
