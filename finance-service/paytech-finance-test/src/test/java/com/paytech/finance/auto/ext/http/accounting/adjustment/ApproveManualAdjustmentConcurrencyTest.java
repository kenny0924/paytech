package com.paytech.finance.auto.ext.http.accounting.adjustment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.paytech.finance.api.accounting.ManualAdjustmentApi;
import com.paytech.finance.api.accounting.request.ReviewManualAdjustmentRequestBody;
import com.paytech.finance.test.base.AbstractManualAdjustmentApiTest;
import com.paytech.finance.test.checker.FinanceResponseChecker;

/**
 * 批准过账并发：同账簿不同调整由序号行锁串行，同一调整由资源锁与状态 CAS 保证只过账一次。
 *
 * @see ManualAdjustmentApi#approveManualAdjustment(String, ReviewManualAdjustmentRequestBody)
 */
class ApproveManualAdjustmentConcurrencyTest extends AbstractManualAdjustmentApiTest {
    private static final int PARALLELISM = 4;

    @Test
    void test_differentAdjustmentsOnSameBookGetDistinctSequences() throws Exception {
        List<Callable<FinanceResponseChecker>> approvals = new ArrayList<>();
        for (int i = 0; i < PARALLELISM; i++) {
            var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "10"));
            long id = submit.id("manualAdjustmentId");
            String hash = submit.text("contentHash");
            approvals.add(() -> approve(id, CHECKER_ID, hash));
        }

        List<FinanceResponseChecker> responses = runConcurrently(approvals);

        responses.forEach(response -> response.assertSuccess(200));
        Set<String> sequences = responses.stream().map(response -> response.text("journalSequence"))
            .collect(Collectors.toSet());
        assertEquals(Set.of("1", "2", "3", "4"), sequences);
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), PARALLELISM)
            .assertAccount(bankCashAccountId, "40", "0", "40", PARALLELISM)
            .assertAccount(capitalAccountId, "0", "40", "40", PARALLELISM)
            .assertProjectionMatchesJournal(bankCashAccountId).assertProjectionMatchesJournal(capitalAccountId);
    }

    @Test
    void test_sameAdjustmentPostedExactlyOnce() throws Exception {
        var submit = submitted(adjustment().transfer(bankCashAccountId, capitalAccountId, "10"));
        long id = submit.id("manualAdjustmentId");
        String hash = submit.text("contentHash");
        List<Callable<FinanceResponseChecker>> approvals = new ArrayList<>();
        for (int i = 0; i < PARALLELISM; i++) {
            approvals.add(() -> approve(id, CHECKER_ID, hash));
        }

        List<FinanceResponseChecker> responses = runConcurrently(approvals);

        Set<String> journalIds = new HashSet<>();
        for (FinanceResponseChecker response : responses) {
            if (response.status() == 200) {
                journalIds.add(response.text("journalEntryId"));
            } else {
                response.assertConcurrentRequest();
            }
        }
        assertEquals(1, journalIds.size(), "所有成功响应应指向同一分录");
        assertEquals(1, testMapper.selectInstructions(id).size());
        ledgerChecker.assertBalancedJournals(ledger.getLedgerBookId(), 1)
            .assertAccount(bankCashAccountId, "10", "0", "10", 1L);
    }

    /** 以同一起跑闩并发执行，有界等待全部完成。 */
    List<FinanceResponseChecker> runConcurrently(List<Callable<FinanceResponseChecker>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<FinanceResponseChecker>> futures = new ArrayList<>();
            for (Callable<FinanceResponseChecker> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<FinanceResponseChecker> results = new ArrayList<>();
            for (Future<FinanceResponseChecker> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "并发请求应在清理前全部结束");
        }
    }
}
