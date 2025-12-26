package com.example.settlementmodule.settlement.domain.repository;

import com.example.settlementmodule.settlement.domain.model.SettlementTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementTargetRepository extends JpaRepository<SettlementTarget, Long> {
    Optional<SettlementTarget> findByOrderId(Long orderId);
    Optional<SettlementTarget> findBySagaId(String sagaId);

    List<SettlementTarget> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(st.amount) FROM SettlementTarget st WHERE st.createdAt >= :start AND st.createdAt < :end")
    BigDecimal sumAmountByDateRange(LocalDateTime start, LocalDateTime end);
}
