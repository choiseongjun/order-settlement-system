package com.example.order.infrastructure.saga;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findByAggregateId(Long aggregateId);

    List<SagaInstance> findByStatus(SagaStatus status);

    Page<SagaInstance> findByStatus(SagaStatus status, Pageable pageable);

    List<SagaInstance> findByStatusAndCreatedAtBefore(SagaStatus status, LocalDateTime cutoff);

    long countByStatus(SagaStatus status);
}
