package com.paytech.channel.connector.auto.ext.http.externalapi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;

import com.paytech.channel.connector.api.externalapi.StandardApiManagementApi;
import com.paytech.channel.connector.api.externalapi.request.CreateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.request.UpdateStandardApiRequestBody;
import com.paytech.channel.connector.api.externalapi.response.StandardApiResponseBody;
import com.paytech.channel.connector.protocol.core.enums.ChangeTargetType;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiRequests;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.checker.ExternalApiResponseChecker;

/**
 * 外部 API 配置写入的并发：全模块配置锁被占用时所有写入返回 409 且不落库，释放后恢复； 同版本并发修改与同编码并发创建只有一个成功。
 *
 * @see StandardApiManagementApi#updateStandardApi(String, String, UpdateStandardApiRequestBody)
 * @see StandardApiManagementApi#createStandardApi(String, CreateStandardApiRequestBody)
 */
class ExternalApiConfigurationConcurrencyTest extends ExternalApiTestBase {

    private static final String LOCK = "lock:external-api-config";
    private static final String STANDARD_APIS = BASE + "/standard-apis";

    @Autowired
    private RedissonClient redisson;


    /** 锁被占用时各类写入在等待上限后返回 409、不落库、不写变更记录；释放后写入成功并释放锁。 */
    @Test
    void test_lockHeldRejectsWrites() {
        externalApi.standardApi("PAY");
        var lock = redisson.getSpinLock(LOCK);
        lock.lock();
        try {
            post(STANDARD_APIS, ExternalApiRequests.createStandardApi("QUERY")).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            put(STANDARD_APIS + "/PAY", ExternalApiRequests.updateStandardApi("改名",
                ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields(), 1))
                .assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
            delete(STANDARD_APIS + "/PAY", ExternalApiRequests.delete(1)).assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        } finally {
            lock.unlock();
        }

        get(STANDARD_APIS + "/QUERY").assertProblem(404, CommonErrorCode.NOT_FOUND);
        assertEquals(1, get(STANDARD_APIS + "/PAY").assertOk().as(StandardApiResponseBody.class).getVersion());
        assertEquals(0, externalApiData.countChanges(ChangeTargetType.STANDARD_API.getCode()));
        assertMutation(post(STANDARD_APIS, ExternalApiRequests.createStandardApi("QUERY")), "STANDARD_API", "QUERY",
            1);
        assertFalse(lock.isLocked());
    }

    /** 两个基于版本 1 的并发修改：一个成功、一个 409；最终版本为 2，名称来自成功的一方。 */
    @Test
    void test_concurrentUpdatesSameVersion() {
        externalApi.standardApi("PAY");

        var results = race(
            () -> put(STANDARD_APIS + "/PAY",
                ExternalApiRequests.updateStandardApi("甲", ExternalApiSamples.standardRequestFields(),
                    ExternalApiSamples.standardResponseFields(), 1)),
            () -> put(STANDARD_APIS + "/PAY", ExternalApiRequests.updateStandardApi("乙",
                ExternalApiSamples.standardRequestFields(), ExternalApiSamples.standardResponseFields(), 1)));

        assertEquals(List.of(200, 409), statuses(results));
        var winner = results.stream().filter(result -> result.status() == 200).findFirst().orElseThrow();
        var loser = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
        winner.assertOk();
        loser.assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST);
        var detail = get(STANDARD_APIS + "/PAY").assertOk().as(StandardApiResponseBody.class);
        assertAll(() -> assertEquals(2, detail.getVersion()),
            () -> assertTrue(List.of("甲", "乙").contains(detail.getStandardApiName())),
            () -> assertEquals(4, detail.getRequestFields().size()));
    }

    /** 两个同编码的并发创建：一个成功；另一个因锁等待超时（409）或编码已存在（400）被拒绝，只留一行。 */
    @Test
    void test_concurrentCreateSameCode() {
        var results = race(() -> post(STANDARD_APIS, ExternalApiRequests.createStandardApi("QUERY")),
            () -> post(STANDARD_APIS, ExternalApiRequests.createStandardApi("QUERY")));

        var statuses = statuses(results);
        assertEquals(200, statuses.getFirst());
        assertTrue(statuses.get(1) == 400 || statuses.get(1) == 409, "失败方状态：" + statuses);
        var detail = get(STANDARD_APIS + "/QUERY").assertOk().as(StandardApiResponseBody.class);
        assertEquals(1, detail.getVersion());
    }

    /** 两个请求在同一时刻起跑，返回各自的响应 Checker；等待有上限。 */
    private List<ExternalApiResponseChecker> race(Supplier<ExternalApiResponseChecker> first,
        Supplier<ExternalApiResponseChecker> second) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<CompletableFuture<ExternalApiResponseChecker>>();
            for (Supplier<ExternalApiResponseChecker> call : List.of(first, second)) {
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

    /** 等待起跑信号，超时即失败。 */
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "并发起跑超时");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    /** 各响应的 HTTP 状态，升序。 */
    private static List<Integer> statuses(List<ExternalApiResponseChecker> results) {
        return results.stream().map(ExternalApiResponseChecker::status).sorted().toList();
    }
}
