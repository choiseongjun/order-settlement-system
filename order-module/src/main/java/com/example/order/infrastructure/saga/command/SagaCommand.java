package com.example.order.infrastructure.saga.command;

/**
 * Saga Command 베이스 인터페이스
 */
public interface SagaCommand {
    String getSagaId();
    String getCommandType();
}
