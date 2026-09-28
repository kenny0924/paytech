package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.compensation.core.execution.CompensationAfterCommitWakeup;
import com.paytech.foundation.compensation.core.execution.CompensationEventWakeup;

/**
 * 验证补偿唤醒只在真实事务成功提交后发生。
 *
 * @author Codex
 */
class CompensationAfterCommitWakeupAutoTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void test_requiresActualTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        CompensationAfterCommitWakeup afterCommitWakeup = createWakeup(new AtomicLong());

        assertThrows(IllegalStateException.class, () -> afterCommitWakeup.registerWakeupAfterCommit(10L, () -> {
            // 不应注册成功。
        }));
    }

    @Test
    void test_wakesOnlyAfterCommit() {
        AtomicLong awakenedEventId = new AtomicLong();
        AtomicInteger observationCount = new AtomicInteger();
        CompensationAfterCommitWakeup afterCommitWakeup = createWakeup(awakenedEventId);
        beginTransactionSynchronization();

        afterCommitWakeup.registerWakeupAfterCommit(20L, observationCount::incrementAndGet);

        assertEquals(0L, awakenedEventId.get());
        assertEquals(0, observationCount.get());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertEquals(20L, awakenedEventId.get());
        assertEquals(1, observationCount.get());
    }

    @Test
    void test_rollbackDoesNotWakeEvent() {
        AtomicLong awakenedEventId = new AtomicLong();
        AtomicInteger observationCount = new AtomicInteger();
        CompensationAfterCommitWakeup afterCommitWakeup = createWakeup(awakenedEventId);
        beginTransactionSynchronization();

        afterCommitWakeup.registerWakeupAfterCommit(30L, observationCount::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertEquals(0L, awakenedEventId.get());
        assertEquals(0, observationCount.get());
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private CompensationAfterCommitWakeup createWakeup(AtomicLong awakenedEventId) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("compensationEventWakeup", (CompensationEventWakeup)awakenedEventId::set);
        return new CompensationAfterCommitWakeup(beanFactory.getBeanProvider(CompensationEventWakeup.class));
    }
}
