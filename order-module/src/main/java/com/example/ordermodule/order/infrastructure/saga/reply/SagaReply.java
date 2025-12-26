package com.example.order.infrastructure.saga.reply;

/**
 * Saga Reply 베이스 인터페이스
 */
public interface SagaReply {
    String getSagaId();
    String getReplyType();
    boolean isSuccess();
}
