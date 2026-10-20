package com.paytech.billing.auto.ext.http.fact;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CURRENCY;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.FINANCE_ENTITY_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.MAKER_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

import com.paytech.billing.api.BillingFactCommandApi;
import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingFactEnvelopeChecker;
import com.paytech.billing.test.checker.BillingResponseChecker;

/**
 * 批准计费事实（Maker-Checker）与 BillingFactRecorded 发布：真实 HTTP 入口批准，补偿任务提交后经本机 RabbitMQ 发布到 billing.events
 * <li>每例用一个临时排他队列绑定 billing.events 观察：信封字段、Payload 含引用与全部明细行且可按 billing-api 载荷类反序列化、
 * businessTime 覆盖为批准时间、published_at 落库、补偿任务在确认后删除。</li>
 * <li>覆盖 Checker ≠ Maker、同一复核人重复批准幂等、替代链版本与被替代状态、被替代事实已变化，以及两次并发批准只发布一条事件。</li>
 *
 * @see BillingFactCommandApi#approveBillingFact(String, String, String)
 */
class ApproveBillingFactTest extends AbstractBillingFactApiTest {

    /** 批准：APPROVED、版本 1、复核信息、businessTime = approvedTime；发布一条信封，Payload 含头、引用与全部明细行，published_at 落库。 */
    @Test
    void test_normalFlowPublishesEvent() {
        BillingFactRequestBuilder builder = BillingFactRequestBuilder.template().partner().addLine(
            FactComponentType.PLATFORM_SERVICE_CHARGE, FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID,
            FactPartyRole.FINANCE_ENTITY, FINANCE_ENTITY_ID, "2.5", FactLineDirection.CHARGE);
        long factId = submitDraft(builder);
        Object submittedBusinessTime = factRow(factId).get("business_time");

        BillingResponseChecker approved = approve(factId, CHECKER_ID).assertSuccess(200)
            .assertText("status", BillingFactStatus.APPROVED.getCode()).assertText("factVersion", "1")
            .assertText("checkerId", CHECKER_ID);

        BillingFactEnvelopeChecker envelope = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).payloadShape();
        BillingFactPayload payload = envelope.payload();
        Map<String, Object> row = factRow(factId);
        assertAll(() -> assertEquals(approved.text("approvedTime"), approved.text("businessTime")),
            () -> assertEquals(approved.text("approvedTime"), approved.text("recordedTime")),
            () -> assertEquals(row.get("approved_time"), row.get("business_time")),
            () -> assertNotEquals(submittedBusinessTime, row.get("business_time"), "批准应覆盖提交时写入的业务时间"),
            () -> assertEquals(BillingFactType.MANUAL_ADJUSTMENT.getCode(), payload.getFactType()),
            () -> assertEquals(FactOriginType.MANUAL.getCode(), payload.getOriginType()),
            () -> assertEquals(BillingFactStatus.APPROVED.getCode(), payload.getStatus()),
            () -> assertEquals(1L, payload.getFactVersion()), () -> assertNull(payload.getSupersedesFactId()),
            () -> assertEquals(builder.sourceReference(), payload.getSourceEventKey()),
            () -> assertEquals(payload.getRecordedTime(), payload.getBusinessTime()),
            () -> assertEquals(Long.valueOf(CLIENT_ID), payload.getClientId()),
            () -> assertEquals(ENTITY_NO, payload.getEntityNo()),
            () -> assertEquals(Long.valueOf(LOCAL_PARTNER_ID), payload.getLocalPartnerId()),
            () -> assertEquals(Long.valueOf(FINANCE_ENTITY_ID), payload.getFinanceEntityId()),
            () -> assertEquals(CURRENCY, payload.getCurrency()),
            () -> assertEquals(new BigDecimal("12.50"), payload.getGrossAmount()),
            () -> assertNull(payload.getBalanceAvailableTime()), () -> assertNotNull(row.get("published_at")),
            () -> assertEquals(1, payload.getRefs().size()),
            () -> assertEquals(FactRefType.TICKET.getCode(), payload.getRefs().get(0).getRefType()),
            () -> assertEquals(List.of(), events.events(factId), "发布确认后补偿任务应删除"));
        List<BillingFactLinePayload> lines = payload.getLines();
        assertAll(() -> assertEquals(2, lines.size()),
            () -> assertEquals(FactComponentType.CLIENT_PRINCIPAL.getCode(), lines.get(0).getComponentType()),
            () -> assertEquals(FactPartyRole.OPERATING_ENTITY.getCode(), lines.get(0).getPayerRole()),
            () -> assertEquals(ENTITY_NO, lines.get(0).getPayerId()),
            () -> assertEquals(FactPartyRole.CLIENT.getCode(), lines.get(0).getPayeeRole()),
            () -> assertEquals(new BigDecimal("10.00"), lines.get(0).getPostedAmount()),
            () -> assertNull(lines.get(0).getTargetBucket()),
            () -> assertEquals(FactComponentType.PLATFORM_SERVICE_CHARGE.getCode(), lines.get(1).getComponentType()),
            () -> assertEquals(FINANCE_ENTITY_ID, lines.get(1).getPayeeId()),
            () -> assertEquals(FactLineDirection.CHARGE.getCode(), lines.get(1).getDirection()),
            () -> assertEquals(new BigDecimal("2.50"), lines.get(1).getPostedAmount()));
        assertNotNull(getFact(factId).assertSuccess(200).assertText("status", BillingFactStatus.APPROVED.getCode())
            .text("publishedAt"), "详情应返回发布确认时间");
    }

    /** 复核人不能是编制人；不存在的事实拒绝；缺少操作人 Header 为参数错误；均不登记发布任务。 */
    @Test
    void test_checkerMustDifferFromMaker() {
        long factId = submitDraft(BillingFactRequestBuilder.template());

        approve(factId, null).assertInvalidRequest();
        approve(factId, MAKER_ID).assertFailCode(BillingFactErrorCode.SELF_REVIEW_FORBIDDEN);
        approve(9_999_999L, CHECKER_ID).assertBusinessRejected("计费事实不存在");

        assertEquals(BillingFactStatus.DRAFT.getCode(), factRow(factId).get("status"));
        events.assertNoEnvelope(factId);
    }

    /** 同一复核人重复批准返回原结果且不重复登记事件；其他复核人拒绝。 */
    @Test
    void test_repeatedApprovalIsIdempotent() {
        long factId = submitDraft(BillingFactRequestBuilder.template());
        BillingResponseChecker first = approve(factId, CHECKER_ID).assertSuccess(200);
        String eventId = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).eventId();

        BillingResponseChecker again = approve(factId, CHECKER_ID).assertSuccess(200);
        approve(factId, OTHER_CHECKER_ID).assertFailCode(BillingFactErrorCode.FACT_NOT_DRAFT);

        assertEquals(first.text("approvedTime"), again.text("approvedTime"));
        assertEquals(first.text("businessTime"), again.text("businessTime"));
        assertEquals(List.of(), events.events(factId), "重复批准不应再登记发布任务");
        events.assertNoEnvelope(factId);
        assertEquals(BillingFactPayload.AGGREGATE_TYPE + ":" + factId + ":" + BillingFactPayload.EVENT_TYPE + ":1", eventId);
    }

    /** 替代链：B 替代 A，批准 B 后 B 版本 2、A 为 SUPERSEDED；B 的事件带 supersedesFactId 与版本 2。 */
    @Test
    void test_supersedeChain() {
        long original = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long correction = submitDraft(BillingFactRequestBuilder.template().supersedes(original).clearLines().addLine(
            FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT, CLIENT_ID,
            "8.00", FactLineDirection.CHARGE));

        approve(correction, CHECKER_ID).assertSuccess(200).assertText("factVersion", "2")
            .assertText("supersedesFactId", Long.toString(original));

        BillingFactPayload payload = events.awaitEnvelope(correction).envelope(correction, 2, instanceId).payload();
        assertAll(() -> assertEquals(Long.valueOf(original), payload.getSupersedesFactId()),
            () -> assertEquals(2L, payload.getFactVersion()),
            () -> assertEquals(new BigDecimal("8.00"), payload.getGrossAmount()),
            () -> assertEquals(BillingFactStatus.SUPERSEDED.getCode(), factRow(original).get("status")),
            () -> assertEquals(1L, ((Number)factRow(original).get("fact_version")).longValue()));
        getFact(original).assertSuccess(200).assertText("status", BillingFactStatus.SUPERSEDED.getCode());
    }

    /** 两条更正事实替代同一事实：先批准者生效，后者因被替代事实已不是 APPROVED 被拒绝且整体回滚；替代 DRAFT 事实在提交时拒绝。 */
    @Test
    void test_supersededFactNoLongerApproved() {
        long original = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        long first = submitDraft(BillingFactRequestBuilder.template().supersedes(original));
        long second = submitDraft(BillingFactRequestBuilder.template().supersedes(original));
        long draft = submitDraft(BillingFactRequestBuilder.template());

        approve(first, CHECKER_ID).assertSuccess(200);
        approve(second, CHECKER_ID).assertFailCode(BillingFactErrorCode.FACT_SUPERSEDE_INVALID);
        submit(BillingFactRequestBuilder.template().supersedes(draft))
            .assertFailCode(BillingFactErrorCode.FACT_SUPERSEDE_INVALID);

        events.awaitEnvelope(first);
        assertAll(() -> assertEquals(BillingFactStatus.DRAFT.getCode(), factRow(second).get("status")),
            () -> assertEquals(0L, ((Number)factRow(second).get("fact_version")).longValue()));
        events.assertNoEnvelope(second);
    }

    /** 两个复核请求并发批准同一事实：共用资源锁串行，只有一次进入 DRAFT 分支，只登记并发布一条事件。 */
    @Test
    void test_concurrentApprovalPublishesOnce() throws Exception {
        long factId = submitDraft(BillingFactRequestBuilder.template());
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
        assertEquals(BillingFactStatus.APPROVED.getCode(), factRow(factId).get("status"));
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
