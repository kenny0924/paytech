package com.paytech.foundation.compensation.core.execution;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在本地事务成功提交后唤醒补偿执行器。
 *
 * <p>
 * 本组件只注册事务同步回调，不会在调用时立即执行补偿任务。事务回滚时不会触发唤醒；即使进程在提交后、 唤醒前退出，数据库扫描任务仍可恢复该事件。
 *
 * @author Codex
 */
public final class CompensationAfterCommitWakeup {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompensationAfterCommitWakeup.class);

    private final ObjectProvider<CompensationEventWakeup> wakeupProvider;

    public CompensationAfterCommitWakeup(ObjectProvider<CompensationEventWakeup> wakeupProvider) {
        this.wakeupProvider = Objects.requireNonNull(wakeupProvider, "wakeupProvider must not be null");
    }

    /**
     * 注册事务提交后的事件唤醒和观测动作。
     *
     * <p>
     * {@code afterCommitObservation} 只能用于快速、无 I/O 的指标或观测，不得执行数据库写入、MQ 发布、 远程调用或其他业务副作用。
     *
     * @param eventId
     *            补偿事件标识
     * @param afterCommitObservation
     *            提交后的快速观测动作
     */
    public void registerWakeupAfterCommit(long eventId, Runnable afterCommitObservation) {
        requireActiveTransaction();
        Runnable requiredObservation =
            Objects.requireNonNull(afterCommitObservation, "afterCommitObservation must not be null");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {
                wakeUp(eventId);
                observe(requiredObservation);
            }
        });
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("compensation after-commit wake-up requires an active Spring transaction");
        }
    }

    private void wakeUp(long eventId) {
        try {
            CompensationEventWakeup wakeup = wakeupProvider.getIfAvailable();
            if (wakeup == null) {
                LOGGER.warn("Compensation wake-up unavailable; scanner will recover eventId={}", eventId);
                return;
            }
            wakeup.wakeUp(eventId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation wake-up rejected; scanner will recover eventId={}, exceptionType={}", eventId,
                exception.getClass().getSimpleName());
        }
    }

    private void observe(Runnable observation) {
        try {
            observation.run();
        } catch (RuntimeException exception) {
            LOGGER.warn("Compensation post-commit observation failed, exceptionType={}",
                exception.getClass().getSimpleName());
        }
    }
}
