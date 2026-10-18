package com.paytech.foundation.messaging.test.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.messaging.api.ConsumeTransaction;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.foundation.messaging.api.DomainEventHandler;
import com.paytech.foundation.messaging.api.DomainEventRejectedException;
import com.paytech.foundation.messaging.test.mapper.TestMapper;

/**
 * 自管事务的测试 Handler：记录调用时是否处于事务中，自己开事务写业务行；按观察器配置拒绝或失败。
 */
@TestComponent
@DomainEventHandler(eventType = TestRun.MANAGED_EVENT, strictOrder = false,
    transaction = ConsumeTransaction.HANDLER_MANAGED)
public final class ManagedEntryHandler implements DomainEventConsumer {

    private final TestMapper testMapper;
    private final HandledEventProbe probe;
    private final TransactionTemplate transaction;

    /**
     * 创建 Handler。
     *
     * @param testMapper 测试 Mapper
     * @param probe 观察器
     * @param transactionManager 宿主事务管理器
     */
    public ManagedEntryHandler(TestMapper testMapper, HandledEventProbe probe,
        PlatformTransactionManager transactionManager) {
        this.testMapper = testMapper;
        this.probe = probe;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void consume(DomainEvent event) {
        probe.recordTransactionActive(TransactionSynchronizationManager.isActualTransactionActive());
        if (probe.shouldReject(event.aggregateId())) {
            throw new DomainEventRejectedException("configured rejection for " + event.aggregateId(),
                new IllegalArgumentException("rejected"));
        }
        transaction.executeWithoutResult(status -> TestHandlerSupport.handle(event, testMapper, probe));
    }
}
