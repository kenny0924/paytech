package com.paytech.billing.auto.ext.job;

import static com.paytech.billing.test.builder.CurrencySpecResponseBuilder.EUR;
import static com.paytech.billing.test.builder.CurrencySpecResponseBuilder.USD;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.billing.app.configuration.CurrencySnapshotHealthIndicator;
import com.paytech.billing.app.ext.job.CurrencySnapshotRefreshScheduler;
import com.paytech.billing.core.common.CurrencySpecSnapshot;
import com.paytech.billing.domain.CurrencyRule;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.builder.CurrencySpecResponseBuilder;
import com.paytech.billing.test.mocker.external.CurrencySpecMocker;
import com.paytech.platform.support.api.enums.CurrencyRoundingMode;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

import tools.jackson.databind.JsonNode;

/**
 * 币种快照刷新入口：经真实出站客户端读取 Platform Mock，整体替换或保留快照
 * <li>全局 BeforeEach 已以默认 USD 刷新一次；Platform 503 或非法响应时保留上一份快照、累计连续失败，计费事实登记与就绪探针不受影响（AC-06）。</li>
 * <li>定时检查只在有刷新请求（或未加载、距上次成功满刷新间隔）时回源。</li>
 * <li>就绪探针在首次加载成功前为 DOWN：以纯 Java 方式在新快照上验证（共享应用上下文的快照每例都已加载）。</li>
 *
 * @see CurrencySnapshotRefreshScheduler#refresh()
 * @see CurrencySnapshotRefreshScheduler#refreshIfDue()
 * @see CurrencySnapshotHealthIndicator#health()
 */
class CurrencySnapshotRefreshSchedulerTest extends AbstractBillingFactApiTest {

    /** Platform 503：刷新失败但保留 USD 快照与加载时间，失败计数加一；登记照常，就绪探针仍为 UP。 */
    @Test
    void test_platformUnavailableKeepsSnapshot() {
        Instant loadedAt = currencySnapshot.loadedAt();
        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());

        assertFalse(currencySnapshotRefresher.refresh());

        assertAll(() -> assertNotNull(currencySnapshot.find(USD)), () -> assertEquals(loadedAt, currencySnapshot.loadedAt()),
            () -> assertEquals(1, currencySnapshot.consecutiveFailures()), () -> assertEquals(2, currencySpecs.listCalls()));
        submit(BillingFactRequestBuilder.template()).assertSuccess(201);
        assertReadinessUp();
    }

    /** 整份响应任一条不合法即整体拒绝：快照保持只有 USD（EUR 未被部分写入），登记照常。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void test_invalidPayloadKeepsSnapshot(String scenario, CurrencySpecListResponseBody response) {
        currencySpecs.mockCurrencySpecList(response);

        assertFalse(currencySnapshotRefresher.refresh());

        assertAll(() -> assertNotNull(currencySnapshot.find(USD)), () -> assertNull(currencySnapshot.find(EUR)),
            () -> assertEquals(1, currencySnapshot.consecutiveFailures()));
        submit(BillingFactRequestBuilder.template()).assertSuccess(201);
    }

    /** 已加载且没有刷新请求时定时检查不回源；业务查不到币种置待刷新后，下一次检查回源并清除请求。 */
    @Test
    void test_refreshIfDueOnlyWhenRequested() {
        currencySpecs.mockCurrencySpecs(CurrencySpecResponseBuilder.template().build(),
            CurrencySpecResponseBuilder.template().currency(EUR).build());

        currencySnapshotRefresher.refreshIfDue();
        assertAll(() -> assertEquals(1, currencySpecs.listCalls()), () -> assertNull(currencySnapshot.find(EUR)));

        submit(BillingFactRequestBuilder.template().currency(EUR)).assertBusinessRejected("币种不存在或尚未同步: " + EUR);
        currencySnapshotRefresher.refreshIfDue();

        assertAll(() -> assertEquals(2, currencySpecs.listCalls()), () -> assertNotNull(currencySnapshot.find(EUR)),
            () -> assertFalse(currencySnapshot.refreshRequested()));
    }

    /** 刷新请求遇到 Platform 故障时重新挂起，等待下一个检查周期重试。 */
    @Test
    void test_failedRequestedRefreshStaysRequested() {
        submit(BillingFactRequestBuilder.template().currency(EUR)).assertBusinessRejected("币种不存在或尚未同步: " + EUR);
        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());

        currencySnapshotRefresher.refreshIfDue();

        assertAll(() -> assertEquals(2, currencySpecs.listCalls()), () -> assertTrue(currencySnapshot.refreshRequested()),
            () -> assertEquals(1, currencySnapshot.consecutiveFailures()));
    }

    /** 新快照首次加载成功前为 DOWN（失败不改变），加载后为 UP 并带加载时间；之后刷新失败仍为 UP。 */
    @Test
    void test_healthDownUntilFirstLoad() {
        CurrencySpecSnapshot snapshot = new CurrencySpecSnapshot();
        CurrencySnapshotHealthIndicator indicator = new CurrencySnapshotHealthIndicator(snapshot);
        Instant loadedAt = Instant.parse("2026-10-20T00:00:00Z");

        Health initial = indicator.health();
        snapshot.recordFailure();
        Health failed = indicator.health();
        snapshot.replace(List.of(new CurrencyRule(USD, 2, CurrencyRoundingMode.HALF_UP, CurrencySpecStatus.ACTIVE)),
            loadedAt);
        Health loaded = indicator.health();
        snapshot.recordFailure();
        Health staleButServing = indicator.health();

        assertAll(() -> assertEquals(Status.DOWN, initial.getStatus()), () -> assertEquals(Status.DOWN, failed.getStatus()),
            () -> assertEquals(1, failed.getDetails().get("consecutiveFailures")),
            () -> assertEquals(Status.UP, loaded.getStatus()),
            () -> assertEquals(loadedAt.toString(), loaded.getDetails().get("loadedAt")),
            () -> assertEquals(0, loaded.getDetails().get("consecutiveFailures")),
            () -> assertEquals(Status.UP, staleButServing.getStatus()));
    }

    /** 非法响应：每例都带一条合法 EUR，用来证明整份拒绝。 */
    static Stream<Arguments> invalidResponses() {
        return Stream.of(
            Arguments.of("缺少 items", list((List<CurrencySpecResponseBody>)null)),
            Arguments.of("空元素", list(Arrays.asList(eur(), null))),
            Arguments.of("币种重复", list(List.of(eur(), CurrencySpecResponseBuilder.template().currency(EUR).build()))),
            Arguments.of("小写代码", list(List.of(eur(), CurrencySpecResponseBuilder.template().currency("usd").build()))),
            Arguments.of("缺少代码", list(List.of(eur(), CurrencySpecResponseBuilder.template().currency(null).build()))),
            Arguments.of("小数位越界", list(List.of(eur(), CurrencySpecResponseBuilder.template().minorUnit(9).build()))),
            Arguments.of("负小数位", list(List.of(eur(), CurrencySpecResponseBuilder.template().minorUnit(-1).build()))),
            Arguments.of("目录外舍入方式",
                list(List.of(eur(), CurrencySpecResponseBuilder.template().roundingMode("HALF_EVEN").build()))),
            Arguments.of("目录外状态", list(List.of(eur(), CurrencySpecResponseBuilder.template().status("RETIRED").build()))),
            Arguments.of("缺少状态", list(List.of(eur(), CurrencySpecResponseBuilder.template().status((String)null).build()))));
    }

    /** 一条合法的 EUR。 */
    static CurrencySpecResponseBody eur() {
        return CurrencySpecResponseBuilder.template().currency(EUR).build();
    }

    /** 列表响应体。 */
    static CurrencySpecListResponseBody list(List<CurrencySpecResponseBody> items) {
        CurrencySpecListResponseBody body = new CurrencySpecListResponseBody();
        body.setItems(items);
        return body;
    }

    /** 就绪探针（readiness 组含 currencySnapshot）为 UP。 */
    void assertReadinessUp() {
        EntityExchangeResult<byte[]> response = management("/actuator/health/readiness");
        JsonNode body = objectMapper.readTree(response.getResponseBody());
        assertAll(() -> assertEquals(200, response.getStatus().value()),
            () -> assertEquals(Status.UP.getCode(), body.path("status").asString()));
    }
}
