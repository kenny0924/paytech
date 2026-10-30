package com.paytech.payout.test.probe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 观察本地事务与出站调用的先后（AC-10）：作为 TransactionExecutionListener 由 Spring Boot 挂到事务管理器上，统计提交次数；
 * 出站拦截器在调用线程上报告当时是否处于事务
 * <li>只观察不干预；每例清零。</li>
 */
@TestComponent
public class TransactionBoundaryProbe implements TransactionExecutionListener, Clear {
    private final AtomicInteger commits = new AtomicInteger();

    private final List<String> outbound = new ArrayList<>();

    private final List<String> inTransaction = new ArrayList<>();

    /** 统计已提交的新事务。 */
    @Override
    public void afterCommit(TransactionExecution transaction, Throwable commitFailure) {
        if (commitFailure == null) {
            commits.incrementAndGet();
        }
    }

    /** 出站拦截器上报：服务组与调用时是否处于事务。 */
    public synchronized void recordOutbound(String group, boolean transactionActive) {
        outbound.add(group);
        if (transactionActive) {
            inTransaction.add(group);
        }
    }

    /** 本例已提交事务数。 */
    public int commits() {
        return commits.get();
    }

    /** 本例出站调用的服务组。 */
    public synchronized List<String> outbound() {
        return List.copyOf(outbound);
    }

    /** 所有出站调用都不在事务内。 */
    public synchronized void assertNoOutboundInTransaction() {
        assertTrue(inTransaction.isEmpty(), "出站调用发生在数据库事务内: " + inTransaction);
    }

    /** 由 Foundation TestBase 在每例结束统一调用。 */
    @Override
    public synchronized void clear() {
        commits.set(0);
        outbound.clear();
        inTransaction.clear();
    }
}
