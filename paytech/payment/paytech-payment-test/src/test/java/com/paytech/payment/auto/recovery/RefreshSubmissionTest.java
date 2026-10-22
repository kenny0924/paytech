package com.paytech.payment.auto.recovery;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.constant.Locks;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.service.processor.ConfirmPayResultProcessor;
import com.paytech.payment.test.base.AbstractPayResultTest;

/**
 * 刷新来源提交的支付结果（3DS 全链路 technique 3.3.1）：经真实 HTTP 入口在 Intent 锁内事务外查单（channel-connector 测试服务返回已映射
 * 结论），短事务推进原 PAY 动作。
 *
 * <p>
 * 覆盖：挑战成功（AC-04，交互字段保留、渠道交易引用不重复插入、PaymentSucceeded 只登记一次）、渠道失败与订单关闭（AC-05，Intent 可重新提交）、
 * 无此交易在 10 分钟宽限内外、仍在处理（返回交互）、结果未知改回处理中、金额不一致不推进、5 秒限频（AC-06）、归属 404、Intent 锁冲突返回当前事实。
 *
 * @see PaymentIntentApi#refreshSubmission(String, String, com.paytech.payment.api.execution.request.RefreshSubmissionRequestBody)
 */
class RefreshSubmissionTest extends AbstractPayResultTest {
    @Autowired
    private RedissonClient redisson;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /**
     * 挑战成功：查单 SUCCEEDED 且金额币种一致 → Action、Attempt、Intent 成功，查单请求为冻结三元组上的 QUERY + ORIGINAL_TRANSACTION；
     * 交互类型与期限保留，同一外部编号的渠道交易引用不重复插入，PaymentSucceeded 只发布一次，查单恢复任务随后删除。
     */
    @Test
    void test_succeeded() {
        PendingPay pending = pendingPay("T-RF-1");
        channel.querySucceeded("T-RF-1", AMOUNT, CURRENCY);

        var body = refresh(pending.payment()).getBody();

        var query = channel.queryCalls().getLast();
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), body.get("status")),
            () -> assertEquals(AMOUNT, body.get("capturedAmount")), () -> assertNull(body.get("interaction")),
            () -> assertEquals(ChannelOperation.QUERY.getCode(), query.getOperation()),
            () -> assertEquals(ExecutionPurpose.ORIGINAL_TRANSACTION.getCode(), query.getPurpose()),
            () -> assertNull(query.getCriteria()), () -> assertNull(query.getAmount()),
            () -> assertEquals(String.valueOf(pending.attemptId()),
                ((Map<?, ?>)query.getInput().get("payment")).get("attemptId")),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), mapper.intent(pending.payment().intentId).get("status")),
            () -> assertEquals(1, mapper.transactionRefs().size()),
            () -> assertEquals(InteractionType.THREE_DS_CHALLENGE.getCode(),
                mapper.actionInteraction(pending.payActionId()).get("interactionType")),
            () -> assertNotNull(mapper.actionInteraction(pending.payActionId()).get("interactionExpiresAt")));
        results.awaitEnvelope(pending.attemptId());
    }

    /** 挑战失败：查单 FAILED → Attempt 失败、失败代码取渠道代码、Intent 释放可重新提交（AC-05）。 */
    @Test
    void test_failed() {
        PendingPay pending = pendingPay("T-RF-F");
        channel.queryFailed("T-RF-F", "AUTHENTICATION_FAILED");

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), body.get("status")),
            () -> assertEquals("AUTHENTICATION_FAILED", body.get("failureCode")),
            () -> assertNull(mapper.intent(pending.payment().intentId).get("activeAttemptId")),
            () -> assertEquals(IntentStatus.OPEN.getCode(), mapper.intent(pending.payment().intentId).get("status")),
            () -> assertEquals(1, mapper.transactionRefs().size()));
        results.assertNoSucceeded(pending.attemptId());
    }

    /** 持卡人放弃后渠道关单：查单 CLOSED → Attempt 失败，失败代码 CHANNEL_ORDER_CLOSED。 */
    @Test
    void test_closed() {
        PendingPay pending = pendingPay("T-RF-C");
        channel.queryClosed("T-RF-C");

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), body.get("status")),
            () -> assertEquals(ConfirmPayResultProcessor.CHANNEL_ORDER_CLOSED, body.get("failureCode")));
    }

    /** 渠道无此交易且 PAY 创建未满 10 分钟：视为渠道尚未落单，不推进。 */
    @Test
    void test_notFoundWithinGrace() {
        PendingPay pending = pendingPay("T-RF-N1");
        channel.queryNotFound();

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
    }

    /** 渠道无此交易且 PAY 创建已超过 10 分钟：失败，失败代码 CHANNEL_TRANSACTION_NOT_FOUND。 */
    @Test
    void test_notFoundAfterGrace() {
        PendingPay pending = pendingPay("T-RF-N2");
        mapper.ageAction(pending.payActionId(), 11 * 60);
        channel.queryNotFound();

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), body.get("status")),
            () -> assertEquals(ConfirmPayResultProcessor.CHANNEL_TRANSACTION_NOT_FOUND, body.get("failureCode")));
    }

    /** 仍在验证（刷新页面，AC-10）：保持处理中并返回交互类型与期限，不返回跳转地址，不写库。 */
    @Test
    void test_stillPending() {
        PendingPay pending = pendingPay("T-RF-P");
        int queriesBefore = channel.queryCalls().size();

        var body = refresh(pending.payment()).getBody();

        @SuppressWarnings("unchecked")
        var interaction = (Map<String, Object>)body.get("interaction");
        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertEquals(InteractionType.THREE_DS_CHALLENGE.getCode(), interaction.get("type")),
            () -> assertNull(interaction.get("redirectUrl")),
            () -> assertEquals(queriesBefore + 1, channel.queryCalls().size()),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
    }

    /** 结果未知的 PAY：查单仍处理中时改回 PROCESSING（UNKNOWN 不阻塞恢复）。 */
    @Test
    void test_unknownBackToProcessing() {
        PendingPay pending = unknownPay();
        assertEquals(ActionStatus.UNKNOWN.getCode(), payStatus(pending));
        channel.queryPending(null);

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
    }

    /** 渠道报告成功但金额不一致：不推进，保持处理中（转人工由查单恢复任务判定）。 */
    @Test
    void test_amountMismatchNotAdvanced() {
        PendingPay pending = pendingPay("T-RF-M");
        channel.querySucceeded("T-RF-M", "9.99", CURRENCY);

        var body = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), payStatus(pending)));
        results.assertNoSucceeded(pending.attemptId());
    }

    /** 5 秒限频（AC-06）：同一提交连续刷新只查单一次，第二次直接返回当前事实。 */
    @Test
    void test_rateLimited() {
        PendingPay pending = pendingPay("T-RF-L");
        int queriesBefore = channel.queryCalls().size();

        refresh(pending.payment());
        var second = refresh(pending.payment()).getBody();

        assertAll(() -> assertEquals(queriesBefore + 1, channel.queryCalls().size()),
            () -> assertEquals(AttemptStatus.PROCESSING.getCode(), second.get("status")));
    }

    /** 归属不符：Intent 不属于该 Client 返回 404，不查单。 */
    @Test
    void test_foreignClientNotFound() {
        PendingPay pending = pendingPay("T-RF-X");
        int queriesBefore = channel.queryCalls().size();

        var failure = assertThrows(RestClientResponseException.class, () -> refresh(pending.payment(), "999"));

        assertAll(() -> assertEquals(404, failure.getStatusCode().value()),
            () -> assertEquals(queriesBefore, channel.queryCalls().size()));
    }

    /** Intent 正被其他请求处理（锁冲突）：跳过确认，返回当前事实，不查单。 */
    @Test
    void test_lockConflictReturnsCurrent() {
        PendingPay pending = pendingPay("T-RF-K");
        channel.querySucceeded("T-RF-K", AMOUNT, CURRENCY);
        int queriesBefore = channel.queryCalls().size();
        var lock = redisson.getSpinLock("lock:" + Locks.getIntentLock(pending.payment().intentId));
        lock.lock();
        try {
            var body = refresh(pending.payment()).getBody();

            assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
                () -> assertEquals(queriesBefore, channel.queryCalls().size()));
        } finally {
            lock.unlock();
        }
    }
}
