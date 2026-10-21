package com.paytech.ledger.auto.ext.job;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.app.configuration.CurrencySnapshotHealthIndicator;
import com.paytech.ledger.app.configuration.CurrencySnapshotProperties;
import com.paytech.ledger.app.ext.job.CurrencySnapshotRefreshScheduler;
import com.paytech.ledger.integration.platform.CurrencySpecSnapshot;
import com.paytech.ledger.integration.platform.PlatformCurrencyClient;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.ledger.test.mocker.external.CurrencySpecMocker;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecListResponseBody;
import com.paytech.platform.support.api.referencedata.response.CurrencySpecResponseBody;

/**
 * Platform 币种规则快照刷新与就绪探针（需求 platform-support 币种主数据维护与读取缓存，AC-06、AC-08、Q3）
 * <li>刷新入口经真实出站客户端访问 Platform Mock：503 或任一条非法数据都整体失败，保留上一份快照，过账照常；新启用的币种经一次刷新被识别。</li>
 * <li>检查周期只在未加载、有刷新请求或已满刷新间隔时回源；带刷新请求的回源失败后请求保留，下一周期继续回源。</li>
 * <li>首次加载前就绪为 DOWN：上下文中的快照已由全局 BeforeEach 加载，因此用全新的快照、刷新入口与健康指示器实例验证“未加载”阶段，
 * 它们复用上下文中的真实 Platform 客户端与时钟。</li>
 *
 * @see CurrencySnapshotRefreshScheduler#refreshIfDue()
 * @see CurrencySnapshotRefreshScheduler#refresh()
 * @see CurrencySnapshotHealthIndicator#health()
 */
class CurrencySnapshotRefreshTest extends AbstractTransactionPostingTest {
    /** 就绪探针路径（独立管理端口）。 */
    private static final String READINESS = "/actuator/health/readiness";

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformCurrencyClient platformCurrencyClient;

    @Autowired
    private CurrencySnapshotHealthIndicator healthIndicator;

    /** AC-06：Platform 503 时保留上一份快照（加载时间不变、失败计数累加），就绪仍为 UP，USD 账簿照常过账。 */
    @Test
    void test_platformUnavailableKeepsSnapshot() {
        Instant loadedAt = currencySnapshot.getLoadedAt();
        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());

        currencySnapshotRefresher.refresh();

        assertAll(() -> assertEquals(loadedAt, currencySnapshot.getLoadedAt()),
            () -> assertEquals(1, currencySnapshot.getConsecutiveFailures()),
            () -> assertEquals(2, currencySnapshot.find(CURRENCY).getMinorUnit()),
            () -> assertEquals(Status.UP, healthIndicator.health().getStatus()),
            () -> assertEquals(200, management(READINESS).getStatus().value()));
        assertPaymentPosts();
    }

    /** AC-06：任一条非法或整体结构非法时整份响应拒绝，不部分装入（同批的合法 AED 不出现），保留上一份快照，过账照常。 */
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidResponses")
    void test_invalidPayloadKeepsSnapshot(String scenario, Supplier<CurrencySpecListResponseBody> response) {
        currencySpecs.mockResponse(response.get());

        currencySnapshotRefresher.refresh();

        assertAll(() -> assertNull(currencySnapshot.find("AED"), "非法响应不得部分装入"),
            () -> assertEquals(2, currencySnapshot.find(CURRENCY).getMinorUnit()),
            () -> assertEquals(1, currencySnapshot.getConsecutiveFailures()));
        assertPaymentPosts();
    }

    /** AC-08：Platform 新启用 AED 后一次刷新即被识别，失败计数清零。 */
    @Test
    void test_newCurrencyRecognizedAfterRefresh() {
        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());
        currencySnapshotRefresher.refresh();
        currencySpecs.mockCurrencies(CurrencySpecMocker.usd(), CurrencySpecMocker.active("AED", 3));

        currencySnapshotRefresher.refresh();

        assertAll(() -> assertEquals(3, currencySnapshot.find("AED").getMinorUnit()),
            () -> assertEquals(CurrencySpecStatus.ACTIVE, currencySnapshot.find("AED").getStatus()),
            () -> assertEquals(0, currencySnapshot.getConsecutiveFailures()));
    }

    /** 已加载、无刷新请求且未满刷新间隔（60 秒）时检查周期不回源；有刷新请求时回源一次并清除请求。 */
    @Test
    void test_refreshIfDueOnlyWhenRequested() {
        long platformCalls = currencySpecs.listCalls();
        currencySnapshotRefresher.refreshIfDue();
        assertEquals(platformCalls, currencySpecs.listCalls(), "未到期不应回源");

        currencySpecs.mockCurrencies(CurrencySpecMocker.usd(), CurrencySpecMocker.active("AED", 2));
        currencySnapshot.requestRefresh();
        currencySnapshotRefresher.refreshIfDue();

        assertAll(() -> assertEquals(platformCalls + 1, currencySpecs.listCalls()),
            () -> assertNotNull(currencySnapshot.find("AED")),
            () -> assertFalse(currencySnapshot.isRefreshRequested()));
    }

    /** 带刷新请求的回源失败后请求保留，下一个检查周期继续回源并成功。 */
    @Test
    void test_failedRequestedRefreshRetriedOnNextCheck() {
        currencySnapshot.requestRefresh();
        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());
        currencySnapshotRefresher.refreshIfDue();
        assertAll(() -> assertTrue(currencySnapshot.isRefreshRequested(), "失败后应保留刷新请求"),
            () -> assertEquals(1, currencySnapshot.getConsecutiveFailures()));

        currencySpecs.mockCurrencies(CurrencySpecMocker.usd(), CurrencySpecMocker.active("AED", 2));
        currencySnapshotRefresher.refreshIfDue();

        assertAll(() -> assertNotNull(currencySnapshot.find("AED")),
            () -> assertFalse(currencySnapshot.isRefreshRequested()),
            () -> assertEquals(0, currencySnapshot.getConsecutiveFailures()));
    }

    /** 距上次成功加载已满刷新间隔时，检查周期无须刷新请求也会全量回源（以零间隔的刷新入口驱动同一快照）。 */
    @Test
    void test_staleSnapshotRefreshedAfterInterval() {
        CurrencySnapshotRefreshScheduler zeroInterval = new CurrencySnapshotRefreshScheduler(clock, currencySnapshot,
            platformCurrencyClient, new CurrencySnapshotProperties(Duration.ZERO));
        currencySpecs.mockCurrencies(CurrencySpecMocker.usd(), CurrencySpecMocker.active("AED", 2));
        long platformCalls = currencySpecs.listCalls();

        zeroInterval.refreshIfDue();

        assertAll(() -> assertEquals(platformCalls + 1, currencySpecs.listCalls()),
            () -> assertNotNull(currencySnapshot.find("AED")));
    }

    /** Q3 / AC-06：首次加载成功前健康为 DOWN，Platform 不可用时保持 DOWN 并计数；首次加载成功后 UP 并给出加载时间。 */
    @Test
    void test_healthDownUntilFirstLoad() {
        CurrencySpecSnapshot fresh = new CurrencySpecSnapshot();
        CurrencySnapshotRefreshScheduler refresher = new CurrencySnapshotRefreshScheduler(clock, fresh,
            platformCurrencyClient, new CurrencySnapshotProperties(Duration.ofSeconds(60)));
        CurrencySnapshotHealthIndicator indicator = new CurrencySnapshotHealthIndicator(fresh);
        assertAll(() -> assertEquals(Status.DOWN, indicator.health().getStatus()),
            () -> assertNull(fresh.find(CURRENCY), "未加载时任何币种都查不到"));

        currencySpecs.mockFailure(CurrencySpecMocker.unavailable());
        refresher.refreshIfDue();
        Health failed = indicator.health();
        assertAll(() -> assertEquals(Status.DOWN, failed.getStatus()),
            () -> assertEquals(1, failed.getDetails().get("consecutiveFailures")));

        currencySpecs.mockCurrencies(CurrencySpecMocker.usd());
        refresher.refreshIfDue();
        Health loaded = indicator.health();
        assertAll(() -> assertEquals(Status.UP, loaded.getStatus()),
            () -> assertEquals(fresh.getLoadedAt().toString(), loaded.getDetails().get("loadedAt")),
            () -> assertEquals(0, loaded.getDetails().get("consecutiveFailures")),
            () -> assertEquals(2, fresh.find(CURRENCY).getMinorUnit()));
    }

    /** 每条非法响应都附带一条合法的 AED，用于证明不会部分装入。 */
    static Stream<Arguments> invalidResponses() {
        return Stream.of(
            invalid("币种代码不是三位大写字母", () -> item("usd", 2, CurrencySpecStatus.ACTIVE)),
            invalid("小数位超过 8", () -> item(CURRENCY, 9, CurrencySpecStatus.ACTIVE)),
            invalid("小数位为负", () -> item(CURRENCY, -1, CurrencySpecStatus.ACTIVE)),
            invalid("舍入方式无法识别", () -> {
                CurrencySpecResponseBody body = CurrencySpecMocker.usd();
                body.setRoundingMode("HALF_EVEN");
                return body;
            }),
            invalid("状态无法识别", () -> {
                CurrencySpecResponseBody body = CurrencySpecMocker.usd();
                body.setStatus("RETIRED");
                return body;
            }),
            invalid("状态缺失", () -> {
                CurrencySpecResponseBody body = CurrencySpecMocker.usd();
                body.setStatus(null);
                return body;
            }),
            invalid("元素为空", () -> null),
            Arguments.of("币种重复", (Supplier<CurrencySpecListResponseBody>)() -> list(CurrencySpecMocker.usd(),
                CurrencySpecMocker.active("AED", 2), CurrencySpecMocker.currency(CURRENCY, 2, CurrencySpecStatus.INACTIVE))),
            Arguments.of("缺少 items", (Supplier<CurrencySpecListResponseBody>)CurrencySpecListResponseBody::new));
    }

    /** 一条合法 AED 加一条非法元素。 */
    static Arguments invalid(String scenario, Supplier<CurrencySpecResponseBody> invalidItem) {
        return Arguments.of(scenario, (Supplier<CurrencySpecListResponseBody>)() -> list(
            CurrencySpecMocker.active("AED", 2), invalidItem.get()));
    }

    /** 构造指定字段的币种规则。 */
    static CurrencySpecResponseBody item(String currency, int minorUnit, CurrencySpecStatus status) {
        return CurrencySpecMocker.currency(currency, minorUnit, status);
    }

    /** 构造列表响应体，元素允许为空。 */
    static CurrencySpecListResponseBody list(CurrencySpecResponseBody... items) {
        CurrencySpecListResponseBody body = new CurrencySpecListResponseBody();
        body.setItems(Arrays.asList(items));
        return body;
    }

    /** USD 账簿的标准支付批次经真实 Broker 过账成功。 */
    void assertPaymentPosts() {
        BillingFactPayload economics = fact(payment("100"), clientPrincipal("97", CLIENT_ID), clientFee("3", ENTITY_NO));
        AccountPostedPayload posting = paymentPosting(economics, 1, "97", null).build();

        postBatch(economics, posting);

        assertPostingInstruction(instruction(posting, AutoInstructionType.PAYMENT_SUCCEEDED), economics, posting,
            oeLedger.getLedgerBookId(), INITIAL_VERSION_ID, businessDate(economics));
    }
}
