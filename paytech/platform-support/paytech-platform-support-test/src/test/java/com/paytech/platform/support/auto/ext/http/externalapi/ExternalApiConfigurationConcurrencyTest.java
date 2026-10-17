package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.externalapi.IntegrationManagementApi;
import com.paytech.platform.support.api.externalapi.StandardApiManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateIntegrationRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.platform.support.api.externalapi.response.IntegrationResponseBody;
import com.paytech.platform.support.api.externalapi.response.StandardApiResponseBody;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiRequests;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;

/**
 * 外部 API 配置写入的并发：全模块配置锁被占用时所有写入返回 409 且不落库，释放后恢复； 同版本并发修改与同编码并发创建只有一个成功。
 *
 * @see StandardApiManagementApi#updateStandardApi(String, String, String, UpdateStandardApiRequestBody)
 * @see IntegrationManagementApi#createIntegration(String, String, CreateIntegrationRequestBody)
 */
class ExternalApiConfigurationConcurrencyTest extends ExternalApiTestBase {

    private static final String LOCK = "lock:external-api-config";
    private static final String STANDARD_APIS = BASE + "/standard-apis";
    private static final String INTEGRATIONS = BASE + "/integrations";

    @Autowired
    private RedissonClient redisson;

    private Operator admin;

    /** 本类公共：一名平台管理员。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
    }

    /** 锁被占用时各类写入在等待上限后返回 409、不落库；校验接口不受影响；释放后写入成功并释放锁。 */
    @Test
    void test_lockHeldRejectsWrites() {
        externalApi.standardApi("PAY");
        var lock = redisson.getSpinLock(LOCK);
        lock.lock();
        try {
            post(INTEGRATIONS, admin, ExternalApiRequests.createIntegration("DEMO")).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            put(STANDARD_APIS + "/PAY", admin, ExternalApiRequests.updateStandardApi("改名",
                ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields(), 1))
                .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            delete(STANDARD_APIS + "/PAY", admin, ExternalApiRequests.delete(1)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        } finally {
            lock.unlock();
        }

        get(INTEGRATIONS + "/DEMO", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertEquals(1, get(STANDARD_APIS + "/PAY", admin).assertOk().as(StandardApiResponseBody.class).getVersion());
        assertMutation(post(INTEGRATIONS, admin, ExternalApiRequests.createIntegration("DEMO")), "INTEGRATION", "DEMO",
            1);
        assertFalse(lock.isLocked());
    }

    /** 两个基于版本 1 的并发修改：一个成功、一个 409；最终版本为 2，名称来自成功的一方。 */
    @Test
    void test_concurrentUpdatesSameVersion() {
        externalApi.standardApi("PAY");

        var results = race(
            () -> put(STANDARD_APIS + "/PAY", admin,
                ExternalApiRequests.updateStandardApi("甲", ExternalApiSamples.standardRequestFields(),
                    ExternalApiSamples.standardResponseFields(), 1)),
            () -> put(STANDARD_APIS + "/PAY", admin, ExternalApiRequests.updateStandardApi("乙",
                ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields(), 1)));

        assertEquals(List.of(200, 409), statuses(results));
        var winner = results.stream().filter(result -> result.status() == 200).findFirst().orElseThrow();
        var loser = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
        winner.assertOk();
        loser.assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        var detail = get(STANDARD_APIS + "/PAY", admin).assertOk().as(StandardApiResponseBody.class);
        assertAll(() -> assertEquals(2, detail.getVersion()),
            () -> assertTrue(List.of("甲", "乙").contains(detail.getStandardApiName())),
            () -> assertEquals(4, detail.getRequestFields().size()));
    }

    /** 两个同编码的并发创建：一个成功；另一个因锁等待超时（409）或编码已存在（400）被拒绝，只留一行。 */
    @Test
    void test_concurrentCreateSameCode() {
        var results = race(() -> post(INTEGRATIONS, admin, ExternalApiRequests.createIntegration("DEMO")),
            () -> post(INTEGRATIONS, admin, ExternalApiRequests.createIntegration("DEMO")));

        var statuses = statuses(results);
        assertEquals(200, statuses.getFirst());
        assertTrue(statuses.get(1) == 400 || statuses.get(1) == 409, "失败方状态：" + statuses);
        var detail = get(INTEGRATIONS + "/DEMO", admin).assertOk().as(IntegrationResponseBody.class);
        assertEquals(1, detail.getVersion());
    }

    /** 两个请求在同一时刻起跑，返回各自的响应 Checker；等待有上限。 */
    private List<PlatformResponseChecker> race(Supplier<PlatformResponseChecker> first,
        Supplier<PlatformResponseChecker> second) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<CompletableFuture<PlatformResponseChecker>>();
            for (Supplier<PlatformResponseChecker> call : List.of(first, second)) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return call.get();
                }, executor));
            }
            start.countDown();
            return futures.stream().map(future -> future.orTimeout(15, TimeUnit.SECONDS).join()).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "并发起跑超时");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static List<Integer> statuses(List<PlatformResponseChecker> results) {
        return results.stream().map(PlatformResponseChecker::status).sorted().toList();
    }
}
