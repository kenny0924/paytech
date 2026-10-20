package com.paytech.ledger.auto.ext.http.chart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.ledger.api.ChartOfAccountsCommandApi;
import com.paytech.ledger.api.LedgerBookCommandApi;
import com.paytech.ledger.api.enums.LedgerSetupErrorCode;
import com.paytech.ledger.api.request.CreateLedgerBookRequestBody;
import com.paytech.ledger.api.request.UpdateChartAccountRequestBody;
import com.paytech.ledger.core.enums.LedgerEntityType;
import com.paytech.ledger.test.base.AbstractChartAccountApiTest;
import com.paytech.ledger.test.builder.AccountingSetupRequestBuilder;
import com.paytech.ledger.test.builder.ChartAccountRequestBuilder;
import com.paytech.ledger.test.checker.LedgerResponseChecker;

/**
 * 科目维护与新建账簿（采用科目表）共用科目表资源锁：锁被占用时两类入口都等待（LOCK），释放后依次执行；采用后结构修改被冻结。
 *
 * @see ChartOfAccountsCommandApi#updateChartAccount(String, String, String, String, UpdateChartAccountRequestBody)
 * @see LedgerBookCommandApi#createLedgerBook(String, String, CreateLedgerBookRequestBody)
 */
class ChartMaintenanceLockTest extends AbstractChartAccountApiTest {

    @Autowired
    private RedissonClient redissonClient;

    private CreateLedgerBookRequestBody adoptLocal;

    @BeforeEach
    void setUpClassMocks() {
        mockLocalChart();
        adoptLocal = AccountingSetupRequestBuilder.ledgerBook(LedgerEntityType.OPERATING_ENTITY.getCode(), "AE001");
        adoptLocal.setChartCode(LOCAL_CHART);
    }

    /** 新建账簿等待科目表锁。 */
    @Test
    void test_createLedgerBookWaitsForChartLock() throws Exception {
        LedgerResponseChecker response = whileChartLocked(() -> postAsOperator("/ledger-books/v1", adoptLocal));

        response.assertSuccess(201);
    }

    /** 结构修改等待科目表锁；锁释放后在未采用的科目表上成功。 */
    @Test
    void test_structureUpdateWaitsForChartLock() throws Exception {
        LedgerResponseChecker response = whileChartLocked(() -> update(LOCAL_CHART, cash.getId(),
            ChartAccountRequestBuilder.update(cash.getAccountName(), "100102", ASSET_GROUP_NO)));

        response.assertSuccess(200).assertText("accountNo", "100102");
    }

    /** 采用与结构修改串行：先采用，则随后的结构修改被冻结，编号不变。 */
    @Test
    void test_adoptionThenStructureUpdateFrozen() {
        postAsOperator("/ledger-books/v1", adoptLocal).assertSuccess(201);

        update(LOCAL_CHART, cash.getId(), ChartAccountRequestBuilder.update(cash.getAccountName(), "100102",
            ASSET_GROUP_NO)).assertRejected(LedgerSetupErrorCode.CHART_STRUCTURE_FROZEN);
        assertEquals(CASH_NO, reload(cash.getId()).getAccountNo());
    }

    /** 持有科目表锁期间发出请求：1 秒内不应完成；释放后返回结果。 */
    LedgerResponseChecker whileChartLocked(Supplier<LedgerResponseChecker> request) throws Exception {
        RLock lock = redissonClient.getSpinLock("lock:chart:" + localChartId);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        lock.lock();
        try {
            CompletableFuture<LedgerResponseChecker> future = CompletableFuture.supplyAsync(request, executor);
            assertFalse(done(future, 1000), "科目表锁被占用时请求不应完成");
            lock.unlock();
            return future.get(30, TimeUnit.SECONDS);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "并发请求应在清理前全部结束");
        }
    }

    /** 有上限地等待异步结果完成。 */
    boolean done(CompletableFuture<?> future, long millis) throws Exception {
        try {
            future.get(millis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }
}
