package com.paytech.finance.auto.ext.http.accounting.trial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.finance.accounting.core.enums.TrialBalanceStatus;
import com.paytech.finance.test.base.AbstractTrialBalanceApiTest;
import com.paytech.finance.test.checker.FinanceResponseChecker;

/**
 * 试算并发：同一账簿期间的编号连续与资源锁冲突，以及试算等待在途过账持有的序号行锁后才取水位。
 *
 * @see com.paytech.finance.api.accounting.TrialBalanceApi#runTrialBalance(String, String)
 */
class TrialBalanceConcurrencyTest extends AbstractTrialBalanceApiTest {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void test_runNoIsContiguous() {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "1");

        for (int i = 1; i <= 3; i++) {
            runTrial(ledger.getOpenPeriodId()).assertSuccess(201).assertText("runNo", String.valueOf(i));
        }
        assertEquals(3, testMapper.countTrialRuns(ledger.getLedgerBookId()));
    }

    @Test
    void test_resourceLockHeldReturnsConflict() {
        RLock lock = redissonClient.getSpinLock(
            "lock:trial-balance:" + ledger.getLedgerBookId() + ":" + ledger.getOpenPeriodId());
        lock.lock();
        try {
            runTrial(ledger.getOpenPeriodId()).assertConcurrentRequest();
        } finally {
            lock.unlock();
        }
        assertEquals(0, testMapper.countTrialRuns(ledger.getLedgerBookId()));
    }

    @Test
    void test_trialWaitsForInFlightPostingOnSequenceRow() throws Exception {
        postedIn(ledger.getOpenPeriodId(), bankCashAccountId, capitalAccountId, "1");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                testMapper.lockBookSequence(ledger.getLedgerBookId());
                locked.countDown();
                await(release);
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "测试事务应先取得序号行锁");
            CompletableFuture<FinanceResponseChecker> trial =
                CompletableFuture.supplyAsync(() -> runTrial(ledger.getOpenPeriodId()), executor);

            assertFalse(waitDone(trial, 1000), "序号行被锁时试算不应完成");
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);

            trial.get(30, TimeUnit.SECONDS).assertSuccess(201)
                .assertText("status", TrialBalanceStatus.SUCCEEDED.getCode()).assertText("asOfSequence", "1");
        } finally {
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "并发请求应在清理前全部结束");
        }
    }

    /** 有上限地等待异步结果完成。 */
    boolean waitDone(CompletableFuture<?> future, long millis) {
        try {
            future.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 在持锁事务中等待释放信号。 */
    void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "释放信号超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
