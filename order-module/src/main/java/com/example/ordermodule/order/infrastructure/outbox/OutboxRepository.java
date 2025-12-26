package com.example.ordermodule.order.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.status = :status AND o.publishedAt < :cutoff")
    int deleteByStatusAndPublishedAtBefore(
        @Param("status") OutboxStatus status,
        @Param("cutoff") LocalDateTime cutoff
    );
}
