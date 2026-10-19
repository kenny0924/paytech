package com.paytech.billing.auto.ext.eventhandler;

import static com.paytech.billing.test.builder.BillingFactRequestBuilder.CLIENT_ID;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.ENTITY_NO;
import static com.paytech.billing.test.builder.BillingFactRequestBuilder.LOCAL_PARTNER_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.billing.api.enums.BillingFactStatus;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactOriginType;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.core.enums.FactTargetBucket;
import com.paytech.billing.service.handler.BillingFactRecordedHandler;
import com.paytech.billing.test.base.AbstractBillingFactApiTest;
import com.paytech.billing.test.builder.BillingFactRequestBuilder;
import com.paytech.billing.test.checker.BillingFactEnvelopeChecker;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * BillingFactRecordedHandler 入口：直接以补偿任务输入调用真实 Handler，发布到真实本地 Broker
 * <li>载荷含 refs、lines（含 targetBucket）、businessTime 与 balanceAvailableTime；信封聚合类型 BILLING_FACT。</li>
 * <li>补偿重试（确认后、删除前崩溃）再次发布同一 eventId，published_at 保持首次确认时间。</li>
 * <li>事实未批准、版本不符或事件键格式非法为终态失败，不发布。</li>
 *
 * @see BillingFactRecordedHandler#handle(CompensationEvent)
 */
class BillingFactRecordedHandlerTest extends AbstractBillingFactApiTest {
    @Autowired
    private BillingFactRecordedHandler handler;

    /** 已发布事实被补偿重试再次执行：成功并重发同一 eventId，published_at 不变。 */
    @Test
    void test_retryRepublishesSameEvent() {
        long factId = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        Object publishedAt = factRow(factId).get("published_at");

        CompensationHandlerResult result = handler.handle(event(BillingFactRecordedHandler.buildEvent(factId, 1).eventKey()));

        assertEquals(CompensationHandlerResult.success(), result);
        events.awaitEnvelope(factId).envelope(factId, 1, instanceId);
        assertEquals(publishedAt, factRow(factId).get("published_at"));
    }

    /** 载荷携带引用、明细行目标余额桶、业务时间与来源方式；字段顺序与标量字符串形态固定。 */
    @Test
    void test_payloadCarriesRefsLinesAndBusinessTime() {
        long factId = submitApprovedAndPublished(BillingFactRequestBuilder.template().partner().clearLines()
            .addLine(FactComponentType.CLIENT_PRINCIPAL, FactPartyRole.OPERATING_ENTITY, ENTITY_NO, FactPartyRole.CLIENT,
                CLIENT_ID, "4.00", FactLineDirection.ADJUSTMENT, FactTargetBucket.AVAILABLE)
            .addLine(FactComponentType.PARTNER_SHARE, FactPartyRole.OPERATING_ENTITY, ENTITY_NO,
                FactPartyRole.LOCAL_PARTNER, LOCAL_PARTNER_ID, "1.00", FactLineDirection.ADJUSTMENT));

        handler.handle(event(BillingFactRecordedHandler.buildEvent(factId, 1).eventKey()));

        BillingFactEnvelopeChecker envelope = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).payloadShape();
        BillingFactPayload payload = envelope.payload();
        assertAll(() -> assertEquals(FactOriginType.MANUAL.getCode(), payload.getOriginType()),
            () -> assertEquals(BillingFactStatus.APPROVED.getCode(), payload.getStatus()),
            () -> assertEquals(payload.getRecordedTime(), payload.getBusinessTime()),
            () -> assertEquals(1, payload.getRefs().size()),
            () -> assertEquals(FactRefType.TICKET.getCode(), payload.getRefs().get(0).getRefType()),
            () -> assertEquals(FactTargetBucket.AVAILABLE.getCode(), payload.getLines().get(0).getTargetBucket()),
            () -> assertNull(payload.getLines().get(1).getTargetBucket()),
            () -> assertNull(envelope.payloadText("balanceAvailableTime")));
    }

    /** 事实头带余额到期时间：Payload 的 balanceAvailableTime 为 UTC RFC 3339 字符串，并按 billing-api 载荷类反序列化为同一时刻。 */
    @Test
    void test_payloadCarriesBalanceAvailableTime() {
        long factId = submitApprovedAndPublished(BillingFactRequestBuilder.template());
        Instant availableTime = Instant.parse("2026-10-21T04:00:00.123456Z");
        testMapper.updateBalanceAvailableTime(factId, availableTime);

        CompensationHandlerResult result = handler.handle(event(BillingFactRecordedHandler.buildEvent(factId, 1).eventKey()));

        BillingFactEnvelopeChecker envelope = events.awaitEnvelope(factId).envelope(factId, 1, instanceId).payloadShape();
        assertAll(() -> assertEquals(CompensationHandlerResult.success(), result),
            () -> assertEquals(availableTime.toString(), envelope.payloadText("balanceAvailableTime")),
            () -> assertEquals(availableTime, envelope.payload().getBalanceAvailableTime()));
    }

    /** 事实仍为 DRAFT、版本不符或不存在：终态失败，不发布、不写 published_at。 */
    @Test
    void test_factNotPublishable() {
        long draft = submitDraft(BillingFactRequestBuilder.template());
        long approved = submitApprovedAndPublished(BillingFactRequestBuilder.template());

        CompensationHandlerResult draftResult = handler.handle(event(key(draft, 1)));
        CompensationHandlerResult versionResult = handler.handle(event(key(approved, 2)));
        CompensationHandlerResult missingResult = handler.handle(event(key(9_999_999L, 1)));

        Map<String, Object> draftRow = factRow(draft);
        assertAll(() -> assertEquals(terminal("BILLING_FACT_NOT_PUBLISHABLE"), draftResult),
            () -> assertEquals(terminal("BILLING_FACT_NOT_PUBLISHABLE"), versionResult),
            () -> assertEquals(terminal("BILLING_FACT_NOT_PUBLISHABLE"), missingResult),
            () -> assertEquals(BillingFactStatus.DRAFT.getCode(), draftRow.get("status")),
            () -> assertNull(draftRow.get("published_at")));
        events.assertNoEnvelope(draft);
    }

    /** 事件键格式、聚合类型、事件类型或子类型不符，以及带 payloadJson：终态失败。 */
    @Test
    void test_malformedEventKey() {
        CompensationHandlerResult wrongAggregate = handler.handle(event("PAYMENT_ATTEMPT:1:BillingFactRecorded:1"));
        CompensationHandlerResult oldEventName = handler.handle(event("BILLING_FACT:1:EconomicFactRecorded:1"));
        CompensationHandlerResult wrongShape = handler.handle(event(BillingFactPayload.AGGREGATE_TYPE + ":1"));
        CompensationHandlerResult wrongSubType = handler.handle(new CompensationEvent(1L, key(1, 1),
            BillingFactRecordedHandler.EVENT_TYPE, "OTHER", null, 1));
        CompensationHandlerResult withPayload = handler.handle(new CompensationEvent(1L, key(1, 1),
            BillingFactRecordedHandler.EVENT_TYPE, "-", "{}", 1));

        assertAll(() -> assertEquals(terminal("BILLING_FACT_EVENT_INVALID"), wrongAggregate),
            () -> assertEquals(terminal("BILLING_FACT_EVENT_INVALID"), oldEventName),
            () -> assertEquals(terminal("BILLING_FACT_EVENT_INVALID"), wrongShape),
            () -> assertEquals(terminal("BILLING_FACT_EVENT_INVALID"), wrongSubType),
            () -> assertEquals(terminal("BILLING_FACT_EVENT_INVALID"), withPayload));
    }

    /** 补偿任务输入；记录 ID 与执行次数不影响 Handler。 */
    static CompensationEvent event(String eventKey) {
        return new CompensationEvent(1L, eventKey, BillingFactRecordedHandler.EVENT_TYPE, "-", null, 1);
    }

    /** 四段式事件键。 */
    static String key(long factId, long factVersion) {
        return BillingFactPayload.AGGREGATE_TYPE + ":" + factId + ":" + BillingFactPayload.EVENT_TYPE + ":" + factVersion;
    }

    /** 终态失败结果；摘要与 Handler 合同一致。 */
    static CompensationHandlerResult terminal(String errorCode) {
        String summary = "BILLING_FACT_EVENT_INVALID".equals(errorCode) ? "Billing fact event is malformed"
            : "Billing fact is not an approved fact of this version";
        return CompensationHandlerResult.terminalFailure(errorCode, summary);
    }
}
