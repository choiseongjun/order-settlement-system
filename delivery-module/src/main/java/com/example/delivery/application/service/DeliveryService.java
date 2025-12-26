package com.example.delivery.application.service;

import com.example.delivery.domain.model.Delivery;
import com.example.delivery.domain.model.DeliveryStatus;
import com.example.delivery.domain.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;

    /**
     * 배송 생성 처리 (Mock - 90% 성공률)
     */
    @Transactional
    public Delivery createDelivery(Long orderId, Long userId, String address,
                                   String recipientName, String recipientPhone, String sagaId) {
        // Mock 배송 생성 (90% 성공률)
        boolean isSuccess = Math.random() < 0.9;

        Delivery delivery = Delivery.builder()
            .orderId(orderId)
            .userId(userId)
            .address(address)
            .recipientName(recipientName)
            .recipientPhone(recipientPhone)
            .sagaId(sagaId)
            .build();

        if (isSuccess) {
            delivery.create();
            log.info("Delivery created: orderId={}, trackingNumber={}",
                orderId, delivery.getTrackingNumber());
        } else {
            delivery.fail("Delivery creation failed (Mock)");
            log.warn("Delivery failed: orderId={}", orderId);
        }

        return deliveryRepository.save(delivery);
    }

    /**
     * 배송 취소 처리 (보상 트랜잭션)
     */
    @Transactional
    public void cancelDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new RuntimeException("Delivery not found: " + deliveryId));

        delivery.cancel();
        deliveryRepository.save(delivery);

        log.info("Delivery cancelled: deliveryId={}, orderId={}", deliveryId, delivery.getOrderId());
    }
}
