package com.example.settlementmodule.settlement.application.service;

import com.example.settlementmodule.settlement.domain.model.SettlementTarget;
import com.example.settlementmodule.settlement.domain.repository.SettlementTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final SettlementTargetRepository settlementTargetRepository;

    /**
     * 정산 대상 추가
     */
    @Transactional
    public SettlementTarget addSettlementTarget(Long orderId, Long paymentId,
                                                BigDecimal amount, String sagaId) {
        SettlementTarget target = SettlementTarget.builder()
            .orderId(orderId)
            .paymentId(paymentId)
            .amount(amount)
            .sagaId(sagaId)
            .build();

        SettlementTarget saved = settlementTargetRepository.save(target);

        log.info("Settlement target added: orderId={}, amount={}, fee={}, netAmount={}",
            orderId, saved.getAmount(), saved.getFee(), saved.getNetAmount());

        return saved;
    }
}
