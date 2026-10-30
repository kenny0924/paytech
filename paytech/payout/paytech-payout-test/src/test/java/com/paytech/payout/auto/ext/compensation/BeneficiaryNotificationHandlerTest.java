package com.paytech.payout.auto.ext.compensation;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.compensation.api.CompensationEvent;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.foundation.compensation.api.CompensationEventScanner;
import com.paytech.foundation.compensation.api.CompensationHandlerResult;
import com.paytech.foundation.compensation.api.CompensationScanRequest;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.test.builder.SubmitBeneficiaryAccountBuilder;
import com.paytech.platform.support.api.notification.request.SubmitNotificationRequestBody;
import com.paytech.payout.service.handler.BeneficiaryNotificationHandler;
import com.paytech.payout.test.base.AbstractBeneficiaryApiTest;
import com.paytech.payout.test.checker.PayoutResponseChecker;
import com.paytech.payout.test.support.PayoutTestConstants;

/**
 * 收款账户通知补偿任务（AC-09，B10）：Platform 不可用时任务退避保留、账户状态不回滚，重新唤醒后用同一去重键重试成功；
 * 无收件人记录后成功；Local Partner 不调用 Platform、任务直接完成；事件身份非法或事实缺失为终态失败；任务载荷为空，不含原值
 *
 * @see BeneficiaryNotificationHandler#handle(CompensationEvent)
 */
class BeneficiaryNotificationHandlerTest extends AbstractBeneficiaryApiTest {
    @Autowired
    private BeneficiaryNotificationHandler handler;

    @Autowired
    private CompensationEventScanner scanner;

    /** 第一次断连 → ERROR 保留，账户仍 VERIFIED；到期后经正式扫描重试成功，两次去重键相同。 */
    @Test
    void test_retryAfterPlatformUnavailable() {
        platform.fail(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, MockHttpFault.disconnect());
        PayoutResponseChecker verified = verifiedAccount();
        await().atMost(ASYNC_WAIT).until(() -> "ERROR".equals(singleEvent().get("execute_status")));
        Map<String, Object> pending = singleEvent();
        assertAll(() -> assertEquals(BeneficiaryNotificationHandler.EVENT_TYPE, pending.get("event_type")),
            () -> assertEquals(verified.text("beneficiaryAccountId") + ":3", pending.get("event_key")),
            () -> assertEquals(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, pending.get("sub_event_type")),
            () -> assertNull(pending.get("payload_json")),
            () -> assertEquals(BeneficiaryAccountStatus.VERIFIED.getCode(),
                testMapper.selectAccountRows().getFirst().get("status")));

        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, 1);
        testMapper.makeErrorEventsDue();
        scanner.scan(new CompensationScanRequest(10, 0, 1, Set.of(BeneficiaryNotificationHandler.EVENT_TYPE)));
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        List<SubmitNotificationRequestBody> calls = platform.calls();
        assertAll(() -> assertEquals(2, calls.size()),
            () -> assertEquals(calls.get(0).getSourceEventKey(), calls.get(1).getSourceEventKey()));
    }

    /** 该租户没有符合条件的收件人（投递数 0）：任务成功删除，不重试。 */
    @Test
    void test_noRecipients() {
        platform.accept(BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, 0);
        verifiedAccount();

        await().atMost(ASYNC_WAIT).until(() -> platform.calls().size() == 1 && testMapper.countCompensationEvents() == 0);
    }

    /** Local Partner 账户关闭：登记通知任务，但不调用 Platform，任务成功删除。 */
    @Test
    void test_localPartnerSkipsNotification() {
        vault.acceptProtect();
        PayoutResponseChecker created = submit(SubmitBeneficiaryAccountBuilder.template()
            .owner(BeneficiaryOwnerType.LOCAL_PARTNER, PayoutTestConstants.PARTNER_ID)
            .byOperations(PayoutTestConstants.OPS_SUBMITTER_ID).build()).assertSuccess(201);

        operationsCommand("close", created, BeneficiaryCloseReason.OPS.getCode()).assertSuccess(200)
            .assertCode("status", BeneficiaryAccountStatus.CLOSED);
        await().atMost(ASYNC_WAIT).until(() -> testMapper.countCompensationEvents() == 0);
        assertEquals(0, platform.calls().size());
    }

    /** 事件键非法、模板不支持、带载荷、事实不存在：终态失败，不调用 Platform。 */
    @Test
    void test_identityFailClosed() {
        assertAll(
            () -> assertOutcome(handler.handle(new CompensationEvent(1L, "abc", BeneficiaryNotificationHandler.EVENT_TYPE,
                BeneficiaryNotificationHandler.TEMPLATE_VERIFIED, null, 1))),
            () -> assertOutcome(handler.handle(new CompensationEvent(1L, PayoutTestConstants.CLIENT_ID + ":1",
                BeneficiaryNotificationHandler.EVENT_TYPE, "OTHER_TEMPLATE", null, 1))),
            () -> assertOutcome(handler.handle(new CompensationEvent(1L, PayoutTestConstants.CLIENT_ID + ":1",
                BeneficiaryNotificationHandler.EVENT_TYPE, BeneficiaryNotificationHandler.TEMPLATE_CLOSED, "{}", 1))),
            () -> assertOutcome(handler.handle(event(BeneficiaryNotificationHandler.buildEvent(123L, 1,
                BeneficiaryNotificationHandler.TEMPLATE_CLOSED)))));
        assertEquals(0, platform.calls().size());
    }

    /** 唯一一条补偿任务。 */
    Map<String, Object> singleEvent() {
        List<Map<String, Object>> events = testMapper.selectCompensationEvents();
        assertEquals(1, events.size(), events::toString);
        return events.getFirst();
    }

    /** 以登记请求构造一次执行尝试。 */
    static CompensationEvent event(CompensationEventRequest request) {
        return new CompensationEvent(1L, request.eventKey(), request.eventType(), request.subEventType(), null, 1);
    }

    /** 断言终态失败。 */
    static void assertOutcome(CompensationHandlerResult actual) {
        assertEquals(CompensationHandlerResult.Outcome.TERMINAL_FAILURE, actual.outcome(), actual.safeSummary());
    }
}
