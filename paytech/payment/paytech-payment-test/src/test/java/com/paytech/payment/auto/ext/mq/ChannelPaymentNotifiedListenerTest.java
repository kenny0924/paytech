package com.paytech.payment.auto.ext.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.event.ChannelPaymentNotifiedPayload;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.api.DomainEventConsumer;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.ext.mq.ChannelPaymentNotifiedListener;
import com.paytech.payment.test.base.AbstractPayResultTest;
import com.paytech.payment.test.mocker.ChannelNotificationMocker;

/**
 * 渠道支付通知消费（3DS 全链路 technique 3.3.5）：channel-connector.events 的 ChannelPaymentNotified 经真实本地 Broker 进入
 * payment.channel-connector.p0..p3 与生产 Listener，Listener 只把通知当作“去查单”的信号，经确认支付结果推进。
 *
 * <p>
 * 覆盖：通知触发查单并推进到成功（AC-07）；同一通知重复投递按 eventId 跳过、不同通知在终态后不再查单（AC-08）；通知与刷新同时到达只写一次终态、
 * PaymentSucceeded 只发布一次（AC-08）；冻结坐标不一致与支付尝试不存在拒绝进入死信（不影响交易）。
 *
 * @see ChannelPaymentNotifiedListener#consume(DomainEvent)
 * @see DomainEventConsumer#consume(DomainEvent)
 */
class ChannelPaymentNotifiedListenerTest extends AbstractPayResultTest {
    @Autowired
    private ChannelNotificationMocker notifications;

    @Autowired
    private IdGenerator ids;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过；本服务分区队列为空。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        notifications.beforeEach();
    }

    /** 通知到达：Listener 查单得到成功并推进 Action / Attempt / Intent，登记消费，PaymentSucceeded 只发布一次（AC-07）。 */
    @Test
    void test_noticeConfirmsPayment() {
        PendingPay pending = pendingPay("T-MQ-1");
        channel.querySucceeded("T-MQ-1", AMOUNT, CURRENCY);
        int queriesBefore = channel.queryCalls().size();
        double handled = notifications.outcome("handled");

        notifications.publish(notice(pending));
        notifications.awaitOutcome("handled", handled, 1);

        assertAll(() -> assertEquals(ActionStatus.SUCCEEDED.getCode(), payStatus(pending)),
            () -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), mapper.attempt(pending.attemptId()).get("status")),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), mapper.intent(pending.payment().intentId).get("status")),
            () -> assertEquals(queriesBefore + 1, channel.queryCalls().size()),
            () -> assertEquals(1, mapper.consumedEvents(ChannelPaymentNotifiedPayload.EVENT_TYPE)));
        results.awaitEnvelope(pending.attemptId());
    }

    /** 同一通知重复投递按 eventId 跳过；终态后的新通知（渠道重发）只核对终态、不再查单，终态与成功事件都只有一次（AC-08）。 */
    @Test
    void test_duplicateNoticesWriteOnce() {
        PendingPay pending = pendingPay("T-MQ-D");
        channel.querySucceeded("T-MQ-D", AMOUNT, CURRENCY);
        DomainEvent first = notice(pending);
        double handled = notifications.outcome("handled");
        double replayed = notifications.outcome("replayed");

        notifications.publish(first);
        notifications.awaitOutcome("handled", handled, 1);
        int queriesAfterFirst = channel.queryCalls().size();
        notifications.publish(first);
        notifications.publish(notice(pending));
        notifications.awaitOutcome("replayed", replayed, 1);
        notifications.awaitOutcome("handled", handled, 2);

        assertAll(() -> assertEquals(queriesAfterFirst, channel.queryCalls().size(), "终态后不再查单"),
            () -> assertEquals(2, mapper.consumedEvents(ChannelPaymentNotifiedPayload.EVENT_TYPE)),
            () -> assertEquals(1, mapper.transactionRefs().size()),
            () -> assertEquals(ActionStatus.SUCCEEDED.getCode(), payStatus(pending)));
        results.awaitEnvelope(pending.attemptId());
    }

    /** 通知与收银台刷新同时到达：终态只写一次，PaymentSucceeded 只发布一次（AC-08）。 */
    @Test
    void test_noticeAndRefreshConcurrent() {
        PendingPay pending = pendingPay("T-MQ-C");
        channel.querySucceeded("T-MQ-C", AMOUNT, CURRENCY);
        double handled = notifications.outcome("handled");

        var refreshed = CompletableFuture.supplyAsync(() -> refresh(pending.payment()));
        notifications.publish(notice(pending));
        notifications.awaitOutcome("handled", handled, 1);
        var refreshedStatus = refreshed.join().getBody().get("status");
        await().atMost(Duration.ofSeconds(10)).until(() -> ActionStatus.SUCCEEDED.getCode().equals(payStatus(pending)));

        assertAll(() -> assertTrue(AttemptStatus.SUCCEEDED.getCode().equals(refreshedStatus)
                || AttemptStatus.PROCESSING.getCode().equals(refreshedStatus), "刷新返回当前事实"),
            () -> assertEquals(1, mapper.transactionRefs().size()),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), mapper.intent(pending.payment().intentId).get("status")));
        results.awaitEnvelope(pending.attemptId());
    }

    /** 冻结渠道或账号与通知不一致、支付尝试不存在：拒绝进入死信，不查单，交易不受影响。 */
    @Test
    void test_invalidNoticeDeadLettered() {
        PendingPay pending = pendingPay("T-MQ-X");
        var attempt = mapper.attempt(pending.attemptId());
        int queriesBefore = channel.queryCalls().size();
        double dead = notifications.outcome("dead");

        notifications.publish(ChannelNotificationMocker.event(ids.nextId(), ids.nextId(),
            ((Number)attempt.get("channelAccountId")).longValue(), String.valueOf(pending.attemptId())));
        notifications.publish(ChannelNotificationMocker.event(ids.nextId(), ((Number)attempt.get("channelId")).longValue(),
            ((Number)attempt.get("channelAccountId")).longValue(), String.valueOf(ids.nextId())));
        notifications.awaitOutcome("dead", dead, 2);

        assertAll(() -> assertNotNull(notifications.receiveDeadLetter(5_000)),
            () -> assertNotNull(notifications.receiveDeadLetter(5_000)),
            () -> assertEquals(queriesBefore, channel.queryCalls().size()),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)),
            () -> assertEquals(0, mapper.consumedEvents(ChannelPaymentNotifiedPayload.EVENT_TYPE)));
    }

    /** 本例支付尝试冻结坐标上的一条新通知（新的回调 ID）。 */
    private DomainEvent notice(PendingPay pending) {
        var attempt = mapper.attempt(pending.attemptId());
        return ChannelNotificationMocker.event(ids.nextId(), ((Number)attempt.get("channelId")).longValue(),
            ((Number)attempt.get("channelAccountId")).longValue(), String.valueOf(pending.attemptId()));
    }
}
