package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.MAKER_ID;
import static com.paytech.billing.test.builder.EconomicFactRequestBuilder.PARTNER_AGREEMENT_OE_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.paytech.billing.api.EconomicFactCommandApi;
import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.api.enums.EconomicsType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactLinePayload;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.billing.test.base.AbstractEconomicFactApiTest;
import com.paytech.billing.test.builder.EconomicFactRequestBuilder;
import com.paytech.billing.test.checker.BillingResponseChecker;
import com.paytech.billing.test.checker.EconomicFactEnvelopeChecker;

/**
 * 批准经济事实（Maker-Checker）与 EconomicFactRecorded 发布：真实 HTTP 入口批准，补偿任务提交后经本机 RabbitMQ 发布到 billing.events
 * <li>每例用一个临时排他队列绑定 billing.events 观察：信封字段、Payload 含全部明细行且可按 billing-api 载荷类反序列化、published_at 落库、
 * 补偿任务在确认后删除。</li>
 * <li>覆盖 Checker ≠ Maker、同一复核人重复批准幂等、替代链版本与被替代状态、被替代事实已变化，以及两次并发批准只发布一条事件。</li>
 *
 * @see EconomicFactCommandApi#approveEconomicFact(String, com.paytech.billing.api.request.ApproveEconomicFactRequestBody)
 */
class ApproveEconomicFactTest extends AbstractEconomicFactApiTest {

    /** 批准：APPROVED、版本 1、复核信息；发布一条信封，Payload 含头与全部明细行，published_at 落库，补偿任务删除。 */
    @Test
    void test_normalFlowPublishesEvent() {
        EconomicFactRequestBuilder builder = EconomicFactRequestBuilder.template().partner().addLine(
            AmountSource.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "2.5", FactLineDirection.CHARGE);
        long factId = submitDraft(builder);

        approve(factId, CHECKER_ID).assertSuccess(200).assertText("status", EconomicFactStatus.APPROVED.getCode())
            .assertText("factVersion", "1").assertText("checkerId", CHECKER_ID);

        EconomicFactEnvelopeChecker envelope = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).payloadShape();
        EconomicFactPayload payload = envelope.payload();
        Map<String, Object> row = factRow(factId);
        assertAll(() -> assertEquals(EconomicsType.MANUAL_ADJUSTMENT.getCode(), payload.getFactType()),
            () -> assertEquals(1L, payload.getFactVersion()), () -> assertNull(payload.getSupersedesFactId()),
            () -> assertEquals(builder.sourceReference(), payload.getSourceEventKey()),
            () -> assertEquals(Long.valueOf(CLIENT_ID), payload.getClientId()),
            () -> assertEquals(ENTITY_NO, payload.getEntityNo()),
            () -> assertEquals(Long.valueOf(LOCAL_PARTNER_ID), payload.getLocalPartnerId()),
            () -> assertEquals(Long.valueOf(PARTNER_AGREEMENT_OE_ID), payload.getPartnerAgreementOeId()),
            () -> assertEquals(Long.valueOf(FINANCE_ENTITY_ID), payload.getFinanceEntityId()),
            () -> assertEquals(CURRENCY, payload.getCurrency()),
            () -> assertEquals(new BigDecimal("12.50"), payload.getGrossAmount()),
            () -> assertNotNull(payload.getRecordedTime()), () -> assertNull(payload.getBalanceAvailableTime()),
            () -> assertNull(envelope.payloadText("balanceAvailableTime")), () -> assertNotNull(row.get("published_at")),
            () -> assertEquals(List.of(), events.events(factId), "发布确认后补偿任务应删除"));
        List<EconomicFactLinePayload> lines = payload.getLines();
        assertAll(() -> assertEquals(2, lines.size()),
            () -> assertEquals(AmountSource.CLIENT_PRINCIPAL.getCode(), lines.get(0).getComponentType()),
            () -> assertEquals(FactPartyRole.OPERATING_ENTITY.getCode(), lines.get(0).getPayerRole()),
            () -> assertEquals(ENTITY_NO, lines.get(0).getPayerId()),
            () -> assertEquals(FactPartyRole.CLIENT.getCode(), lines.get(0).getPartyType()),
            () -> assertEquals(new BigDecimal("10.00"), lines.get(0).getPostedAmount()),
            () -> assertEquals(AmountSource.PLATFORM_SERVICE_CHARGE.getCode(), lines.get(1).getComponentType()),
            () -> assertEquals(FINANCE_ENTITY_ID, lines.get(1).getPartyId()),
            () -> assertEquals(FactLineDirection.CHARGE.getCode(), lines.get(1).getDirection()),
            () -> assertEquals(new BigDecimal("2.50"), lines.get(1).getPostedAmount()));
        assertNotNull(getFact(factId).assertSuccess(200).assertText("status", EconomicFactStatus.APPROVED.getCode())
            .text("publishedAt"), "详情应返回发布确认时间");
    }

    /** 复核人不能是编制人；不存在的事实拒绝；均不登记发布任务。 */
    @Test
    void test_checkerMustDifferFromMaker() {
        long factId = submitDraft(EconomicFactRequestBuilder.template());

        approve(factId, MAKER_ID).assertBusinessRejected("复核人不能是编制人");
        approve(9_999_999L, CHECKER_ID).assertBusinessRejected("经济事实不存在");

        assertEquals(EconomicFactStatus.DRAFT.getCode(), factRow(factId).get("status"));
        events.assertNoEnvelope(factId);
    }

    /** 同一复核人重复批准返回原结果且不重复登记事件；其他复核人拒绝。 */
    @Test
    void test_repeatedApprovalIsIdempotent() {
        long factId = submitDraft(EconomicFactRequestBuilder.template());
        BillingResponseChecker first = approve(factId, CHECKER_ID).assertSuccess(200);
        String eventId = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).eventId();

        BillingResponseChecker again = approve(factId, CHECKER_ID).assertSuccess(200);
        approve(factId, OTHER_CHECKER_ID).assertBusinessRejected("已由其他复核人批准");

        assertEquals(first.text("approvedTime"), again.text("approvedTime"));
        assertEquals(List.of(), events.events(factId), "重复批准不应再登记发布任务");
        events.assertNoEnvelope(factId);
        assertEquals("ECONOMIC_FACT:" + factId + ":EconomicFactRecorded:1", eventId);
    }

    /** 替代链：B 替代 A，批准 B 后 B 版本 2、A 为 SUPERSEDED；B 的事件带 supersedesFactId 与版本 2。 */
    @Test
    void test_supersedeChain() {
        long original = submitApprovedAndPublished(EconomicFactRequestBuilder.template());
        long correction = submitDraft(EconomicFactRequestBuilder.template().supersedes(original).clearLines().addLine(
            AmountSource.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID,
            "8.00", FactLineDirection.CHARGE));

        approve(correction, CHECKER_ID).assertSuccess(200).assertText("factVersion", "2")
            .assertText("supersedesFactId", Long.toString(original));

        EconomicFactPayload payload =
            events.awaitEnvelope(correction).envelope(correction, 2, instanceId).payload();
        assertAll(() -> assertEquals(Long.valueOf(original), payload.getSupersedesFactId()),
            () -> assertEquals(2L, payload.getFactVersion()),
            () -> assertEquals(new BigDecimal("8.00"), payload.getGrossAmount()),
            () -> assertEquals(EconomicFactStatus.SUPERSEDED.getCode(), factRow(original).get("status")),
            () -> assertEquals(1L, ((Number)factRow(original).get("fact_version")).longValue()));
        getFact(original).assertSuccess(200).assertText("status", EconomicFactStatus.SUPERSEDED.getCode());
    }

    /** 两条更正事实替代同一事实：先批准者生效，后者因被替代事实已不是 APPROVED 被拒绝且整体回滚；草稿替代 DRAFT 事实被拒绝。 */
    @Test
    void test_supersededFactNoLongerApproved() {
        long original = submitApprovedAndPublished(EconomicFactRequestBuilder.template());
        long first = submitDraft(EconomicFactRequestBuilder.template().supersedes(original));
        long second = submitDraft(EconomicFactRequestBuilder.template().supersedes(original));
        long draft = submitDraft(EconomicFactRequestBuilder.template());

        approve(first, CHECKER_ID).assertSuccess(200);
        approve(second, CHECKER_ID).assertBusinessRejected("被替代事实已不是已批准状态");
        submit(EconomicFactRequestBuilder.template().supersedes(draft))
            .assertBusinessRejected("被替代事实不存在、不是已批准的同类型同主体同币种事实");

        events.awaitEnvelope(first);
        assertAll(() -> assertEquals(EconomicFactStatus.DRAFT.getCode(), factRow(second).get("status")),
            () -> assertEquals(0L, ((Number)factRow(second).get("fact_version")).longValue()));
        events.assertNoEnvelope(second);
    }

    /** 两个复核请求并发批准同一事实：共用资源锁串行，只有一次进入 DRAFT 分支，只登记并发布一条事件。 */
    @Test
    void test_concurrentApprovalPublishesOnce() throws Exception {
        long factId = submitDraft(EconomicFactRequestBuilder.template());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<CompletableFuture<BillingResponseChecker>> calls = List.of(
                CompletableFuture.supplyAsync(() -> awaitAndApprove(start, factId), pool),
                CompletableFuture.supplyAsync(() -> awaitAndApprove(start, factId), pool));
            start.countDown();
            for (CompletableFuture<BillingResponseChecker> call : calls) {
                call.get(30, TimeUnit.SECONDS).assertSuccess(200).assertText("factVersion", "1");
            }
        } finally {
            pool.shutdownNow();
        }

        events.awaitEnvelope(factId).envelope(factId, 1, instanceId);
        assertEquals(EconomicFactStatus.APPROVED.getCode(), factRow(factId).get("status"));
    }

    /** 等待同时开始后批准。 */
    BillingResponseChecker awaitAndApprove(CountDownLatch start, long factId) {
        try {
            start.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
        return approve(factId, CHECKER_ID);
    }
}
