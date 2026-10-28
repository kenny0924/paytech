package com.paytech.ledger.auto.domainservice.posting;

import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientFee;
import static com.paytech.ledger.test.builder.BillingFactLineBuilder.clientPrincipal;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;

import com.paytech.account.api.event.AccountPostedPayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.ledger.api.enums.AutoInstructionType;
import com.paytech.ledger.test.base.AbstractTransactionPostingTest;
import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.test.ReferenceDataBuilders;

/**
 * ledger 接入参考数据客户端后的币种快照刷新与就绪（参考数据客户端组件 AC-03、AC-04、AC-07；原币种快照需求 AC-06、AC-08）
 * <li>刷新经真实客户端访问 Platform Mock：503 时该种类本次失败、保留上一份快照，就绪仍为 UP，USD 账簿照常过账。</li>
 * <li>Platform 新启用的币种经一次全量刷新被识别。加载、校验、未就绪与周期等通用行为由客户端模块自己的 auto 测试覆盖，这里只验证 ledger 的接入。</li>
 *
 * @see ReferenceDataRefresher#refreshNow()
 */
class ReferenceDataRefreshPostingTest extends AbstractTransactionPostingTest {
    /** 就绪探针路径（独立管理端口）。 */
    private static final String READINESS = "/actuator/health/readiness";

    @Autowired
    @Qualifier("referenceData")
    private HealthIndicator referenceDataHealth;

    /** Platform 503 时保留上一份快照，就绪仍为 UP（readiness 组 include referenceData），USD 账簿照常过账。 */
    @Test
    void test_platformUnavailableKeepsSnapshot() {
        referenceDataMocker.unavailable(ReferenceDataKind.CURRENCY);

        List<ReferenceDataRefresher.Result> results = referenceDataRefresher.refreshNow();

        assertAll(() -> assertFalse(results.get(0).succeeded(), "503 时本次刷新应失败"),
            () -> assertEquals(2, referenceData.currencies().require(CURRENCY).minorUnit()),
            () -> assertEquals(Status.UP, referenceDataHealth.health().getStatus()),
            () -> assertEquals(200, management(READINESS).getStatus().value()));
        assertPaymentPosts();
    }

    /** Platform 新启用 AED（3 位）后一次全量刷新即被识别；请求带本服务的 X-Caller-Service。 */
    @Test
    void test_newCurrencyRecognizedAfterRefresh() {
        refreshCurrencies(ReferenceDataBuilders.currency(CURRENCY), ReferenceDataBuilders.currency("AED").minorUnit(3));

        assertAll(() -> assertEquals(3, referenceData.currencies().requireActive("AED").minorUnit()),
            () -> assertEquals(2, referenceData.currencies().requireActive(CURRENCY).minorUnit()),
            () -> assertEquals("ledger-service", referenceDataMocker.lastCallerService(), "请求应带 X-Caller-Service"));
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
