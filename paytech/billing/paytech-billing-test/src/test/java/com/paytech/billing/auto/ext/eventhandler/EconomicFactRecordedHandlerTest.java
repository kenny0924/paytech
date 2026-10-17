package com.paytech.billing.auto.ext.eventhandler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.billing.api.enums.EconomicFactStatus;
import com.paytech.billing.service.handler.EconomicFactRecordedHandler;
import com.paytech.billing.test.base.AbstractEconomicFactApiTest;
import com.paytech.billing.test.builder.EconomicFactRequestBuilder;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * EconomicFactRecordedHandler 入口：直接以补偿任务输入调用真实 Handler，发布到真实本地 Broker
 * <li>补偿重试（确认后、删除前崩溃）再次发布同一 eventId，published_at 保持首次确认时间。</li>
 * <li>事实未批准、版本不符或事件键格式非法为终态失败，不发布。</li>
 */
class EconomicFactRecordedHandlerTest extends AbstractEconomicFactApiTest {
    @Autowired
    private EconomicFactRecordedHandler handler;

    /** 已发布事实被补偿重试再次执行：成功并重发同一 eventId，published_at 不变。 */
    @Test
    void test_retryRepublishesSameEvent() {
        long factId = submitApprovedAndPublished(EconomicFactRequestBuilder.template());
        Object publishedAt = factRow(factId).get("published_at");

        CompensationHandlerResult result = handler.handle(event(EconomicFactRecordedHandler.buildEvent(factId, 1).eventKey()));

        assertEquals(CompensationHandlerResult.success(), result);
        events.awaitEnvelope(factId).envelope(factId, 1, instanceId);
        assertEquals(publishedAt, factRow(factId).get("published_at"));
    }

    /** 事实仍为 DRAFT、版本不符或不存在：终态失败，不发布、不写 published_at。 */
    @Test
    void test_factNotPublishable() {
        long draft = submitDraft(EconomicFactRequestBuilder.template());
        long approved = submitApprovedAndPublished(EconomicFactRequestBuilder.template());

        CompensationHandlerResult draftResult = handler.handle(event(key(draft, 1)));
        CompensationHandlerResult versionResult = handler.handle(event(key(approved, 2)));
        CompensationHandlerResult missingResult = handler.handle(event(key(9_999_999L, 1)));

        Map<String, Object> draftRow = factRow(draft);
        assertAll(() -> assertEquals(terminal("ECONOMIC_FACT_NOT_PUBLISHABLE"), draftResult),
            () -> assertEquals(terminal("ECONOMIC_FACT_NOT_PUBLISHABLE"), versionResult),
            () -> assertEquals(terminal("ECONOMIC_FACT_NOT_PUBLISHABLE"), missingResult),
            () -> assertEquals(EconomicFactStatus.DRAFT.getCode(), draftRow.get("status")),
            () -> assertEquals(null, draftRow.get("published_at")));
        events.assertNoEnvelope(draft);
    }

    /** 事件键格式、聚合类型或子类型不符：终态失败。 */
    @Test
    void test_malformedEventKey() {
        CompensationHandlerResult wrongAggregate = handler.handle(event("PAYMENT_ATTEMPT:1:EconomicFactRecorded:1"));
        CompensationHandlerResult wrongShape = handler.handle(event("ECONOMIC_FACT:1"));
        CompensationHandlerResult wrongSubType = handler.handle(new CompensationEvent(1L, key(1, 1),
            EconomicFactRecordedHandler.EVENT_TYPE, "OTHER", null, 1));

        assertAll(() -> assertEquals(terminal("ECONOMIC_FACT_EVENT_INVALID"), wrongAggregate),
            () -> assertEquals(terminal("ECONOMIC_FACT_EVENT_INVALID"), wrongShape),
            () -> assertEquals(terminal("ECONOMIC_FACT_EVENT_INVALID"), wrongSubType));
    }

    /** 补偿任务输入；记录 ID 与执行次数不影响 Handler。 */
    static CompensationEvent event(String eventKey) {
        return new CompensationEvent(1L, eventKey, EconomicFactRecordedHandler.EVENT_TYPE, "-", null, 1);
    }

    /** 四段式事件键。 */
    static String key(long factId, long factVersion) {
        return "ECONOMIC_FACT:" + factId + ":EconomicFactRecorded:" + factVersion;
    }

    /** 终态失败结果；摘要与 Handler 合同一致。 */
    static CompensationHandlerResult terminal(String errorCode) {
        String summary = "ECONOMIC_FACT_EVENT_INVALID".equals(errorCode) ? "Economic fact event is malformed"
            : "Economic fact is not an approved fact of this version";
        return CompensationHandlerResult.terminalFailure(errorCode, summary);
    }
}
