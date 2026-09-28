package com.paytech.risk.test.infrastructure;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 只暂停真实 MyBatis SQL，用于确定性打开并发提交窗口及观察事务边界。 */
@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public final class RiskSqlBarrier implements Interceptor {

    private static final Duration SAFETY_TIMEOUT = Duration.ofSeconds(15);
    private static final Object TRANSACTION_ID_RESOURCE = new Object();
    private static final AtomicLong TRANSACTION_IDS = new AtomicLong();

    private final AtomicReference<Gate> active = new AtomicReference<>();

    public Gate pauseBefore(String statementSuffix, int occurrence) {
        return arm(statementSuffix, occurrence, Phase.BEFORE_QUERY);
    }

    public Gate pauseAfter(String statementSuffix, int occurrence) {
        return arm(statementSuffix, occurrence, Phase.AFTER_QUERY);
    }

    private Gate arm(String statementSuffix, int occurrence, Phase phase) {
        Gate gate = new Gate(statementSuffix, occurrence, phase, active);
        if (!active.compareAndSet(null, gate)) {
            throw new IllegalStateException("已有 Risk SQL 测试屏障尚未释放");
        }
        return gate;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Gate gate = active.get();
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (gate == null || !gate.matches(statement.getId())) {
            return invocation.proceed();
        }
        return gate.intercept(invocation);
    }

    /** 返回当前执行点的 Spring 事务属性及测试进程内事务标识。 */
    public static TransactionObservation observeCurrentTransaction() {
        boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
        return new TransactionObservation(transactionActive,
            TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
            TransactionSynchronizationManager.getCurrentTransactionIsolationLevel(),
            transactionActive ? currentTransactionId() : null);
    }

    private static Long currentTransactionId() {
        Object existing = TransactionSynchronizationManager.getResource(TRANSACTION_ID_RESOURCE);
        if (existing instanceof Long transactionId) {
            return transactionId;
        }
        long transactionId = TRANSACTION_IDS.incrementAndGet();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return transactionId;
        }
        TransactionSynchronizationManager.bindResource(TRANSACTION_ID_RESOURCE, transactionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (Long.valueOf(transactionId).equals(
                    TransactionSynchronizationManager.getResource(TRANSACTION_ID_RESOURCE))) {
                    TransactionSynchronizationManager.unbindResource(TRANSACTION_ID_RESOURCE);
                }
            }
        });
        return transactionId;
    }

    private enum Phase {
        BEFORE_QUERY,

        AFTER_QUERY
    }

    public record TransactionObservation(
        boolean active,
        boolean readOnly,
        Integer isolationLevel,
        Long transactionId
    ) {

        public boolean isReadOnlyRepeatableRead() {
            return active && readOnly
                && Integer.valueOf(Connection.TRANSACTION_REPEATABLE_READ).equals(isolationLevel);
        }
    }

    public static final class Gate implements AutoCloseable {

        private final String statementSuffix;
        private final int targetOccurrence;
        private final Phase phase;
        private final AtomicReference<Gate> owner;
        private final AtomicInteger occurrences = new AtomicInteger();
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<TransactionObservation> observation = new AtomicReference<>();

        private Gate(
            String statementSuffix,
            int targetOccurrence,
            Phase phase,
            AtomicReference<Gate> owner
        ) {
            if (statementSuffix == null || statementSuffix.isBlank() || targetOccurrence <= 0) {
                throw new IllegalArgumentException("SQL 屏障必须指定语句后缀和正匹配序号");
            }
            this.statementSuffix = statementSuffix;
            this.targetOccurrence = targetOccurrence;
            this.phase = phase;
            this.owner = owner;
        }

        public boolean awaitReached(Duration timeout) throws InterruptedException {
            return reached.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
        }

        public TransactionObservation observation() {
            TransactionObservation value = observation.get();
            if (value == null) {
                throw new IllegalStateException("SQL 屏障尚未观察到目标查询");
            }
            return value;
        }

        public void release() {
            release.countDown();
        }

        private boolean matches(String statementId) {
            return statementId.endsWith(statementSuffix);
        }

        private Object intercept(Invocation invocation) throws Throwable {
            if (occurrences.incrementAndGet() != targetOccurrence) {
                return invocation.proceed();
            }
            if (phase == Phase.BEFORE_QUERY) {
                pause();
                return invocation.proceed();
            }
            Object result = invocation.proceed();
            pause();
            return result;
        }

        private void pause() throws InterruptedException {
            observation.set(observeCurrentTransaction());
            reached.countDown();
            if (!release.await(SAFETY_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("Risk SQL 测试屏障等待释放超时: " + statementSuffix);
            }
        }

        @Override
        public void close() {
            release();
            owner.compareAndSet(this, null);
        }
    }
}
