package com.paytech.account.auto.ext.eventhandler;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.service.handler.AccountPostedHandler;
import com.paytech.account.test.base.AbstractBalanceTest;
import com.paytech.account.test.probe.AccountPostedProbe;
import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;

/**
 * AccountPostedHandler 入口：直接以补偿任务输入调用真实 Handler，发布到真实本地 Broker
 * <li>补偿重试（确认后、删除前崩溃）再次发布同一 eventId，published_time 保持首次确认时间。</li>
 * <li>批次不存在或事件键、聚合 ID、子类型非法为终态失败，不发布。</li>
 * <li>正常链路（入账事务登记 → 提交后 Dispatcher 执行）由消费与桶移动用例经 {@link AccountPostedProbe} 覆盖。</li>
 *
 * @see AccountPostedHandler#handle(CompensationEvent)
 */
class AccountPostedHandlerTest extends AbstractBalanceTest {
    @Autowired
    private AccountPostedHandler handler;

    /** 已发布批次被补偿重试再次执行：成功并重发同一 eventId，published_time 不变。 */
    @Test
    void test_retryRepublishesSameEvent() {
        long factId = consumedAndPublished();
        Object publishedTime = factPosting(factId).get("published_time");
        assertNotNull(publishedTime, "首次发布确认后应写 published_time");

        CompensationHandlerResult result = handler.handle(event(AccountPostedHandler.buildEvent(
            AccountEntityType.OPERATING_ENTITY, ENTITY_NO, CURRENCY, 0, 1).eventKey()));

        assertEquals(CompensationHandlerResult.success(), result);
        AccountPostedProbe.Posted republished = posted.await(1).getFirst();
        assertAll(() -> assertEquals(factId, republished.payload().getFactId()),
            () -> assertEquals(publishedTime, factPosting(factId).get("published_time")));
    }

    /** 批次不存在：终态失败，不发布。 */
    @Test
    void test_postingNotFound() {
        CompensationHandlerResult result = handler.handle(event(AccountPostedHandler.buildEvent(
            AccountEntityType.OPERATING_ENTITY, ENTITY_NO, CURRENCY, 0, 7).eventKey()));

        assertEquals(CompensationHandlerResult.terminalFailure("ACCOUNT_POSTING_NOT_FOUND", "Account posting does not exist"),
            result);
        posted.await(0);
    }

    /** 事件键格式、聚合类型、聚合 ID 或子类型不符：终态失败。 */
    @Test
    void test_malformedEventKey() {
        CompensationHandlerResult wrongAggregate = handler.handle(event("ECONOMIC_FACT:1:AccountPosted:1"));
        CompensationHandlerResult wrongStream = handler.handle(event("ACCOUNT_POSTING:AE001.USD.0:AccountPosted:1"));
        CompensationHandlerResult wrongShape = handler.handle(event("ACCOUNT_POSTING:OE.AE001.USD.0"));
        CompensationHandlerResult wrongSubType = handler.handle(new CompensationEvent(1L,
            "ACCOUNT_POSTING:OE.AE001.USD.0:AccountPosted:1", AccountPostedHandler.EVENT_TYPE, "OTHER", null, 1));

        CompensationHandlerResult invalid = CompensationHandlerResult.terminalFailure("ACCOUNT_POSTED_EVENT_INVALID",
            "Account posting event is malformed");
        assertAll(() -> assertEquals(invalid, wrongAggregate), () -> assertEquals(invalid, wrongStream),
            () -> assertEquals(invalid, wrongShape), () -> assertEquals(invalid, wrongSubType));
    }

    /** 入账一条事实并等待首次发布完成（消费观察队列中的首条信封），返回事实主键。 */
    private long consumedAndPublished() {
        EconomicFactPayload fact = clientCredit("3").build();
        consumed(fact);
        posted.await(1);
        await().atMost(CONSUME_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        return fact.getFactId();
    }

    /** 补偿任务输入；记录 ID 与执行次数不影响 Handler。 */
    static CompensationEvent event(String eventKey) {
        return new CompensationEvent(1L, eventKey, AccountPostedHandler.EVENT_TYPE, "-", null, 1);
    }
}
