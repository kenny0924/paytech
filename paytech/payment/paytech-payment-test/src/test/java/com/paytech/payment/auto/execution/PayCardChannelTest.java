package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.action.PayActionActor;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.CardApiMocker;
import com.paytech.payment.test.mocker.ChannelConnectorMocker;
import com.paytech.payment.test.mocker.ChannelConnectorMocker.Candidate;
import com.paytech.payment.test.mocker.PlatformMocker;

/**
 * 卡直付执行链路（PayerMax 卡直付对接技术方案第 5 节）：从 pay 的真实 HTTP 入口经路由、受理事务、风控动作与 PAY 动作，
 * 以真实客户端调用 Risk、card 与 channel-connector 的测试 HTTP 服务（D06：channel-connector 返回已映射结论）。发起渠道调用前 Payment
 * 向 card 申请一次性读卡票据（design/05 §2.1），渠道输入只带票据；测试服务像真实 Platform 一样凭票据向 card 读卡取得卡数据。
 *
 * <p>
 * 覆盖：成功、3DS 待认证（临时交互指令不落库）、发卡行拒绝、结果未知、保证未发出请求、原提交重放不再执行，
 * 以及票据只能使用一次、card 拒签（CVV 已领取）时动作以 NOT_SENT 语义失败且不调用渠道。卡号为公开测试卡号，属合成数据。风控各结论与故障见 {@link PayRiskEvaluationTest}。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PayCardChannelTest extends AbstractPayCardTest {
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    @Autowired
    private PlatformMocker platform;

    private Candidate candidate;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        candidate = channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 成功：channel-connector 收到冻结三元组、OE 与读卡票据（无卡值、无账号材料、不是支付工具 Token），凭票据读卡取得卡号与一次性 CVV；Attempt、Intent 成功并保存渠道交易引用。 */
    @Test
    void test_paySucceeded() {
        channel.succeeded("T-PMX-1");
        var paid = newPayment();

        var response = paid.pay();

        var call = channel.only();
        var request = call.request();
        var input = call.input();
        assertAll(() -> assertEquals(200, response.getStatusCode().value()),
            () -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), response.getBody().get("status")),
            () -> assertNull(response.getBody().get("nextAction")),
            () -> assertEquals(ChannelOperation.PAY.getCode(), request.getOperation()),
            () -> assertEquals(ExecutionPurpose.NEW_PAYMENT.getCode(), request.getPurpose()),
            () -> assertEquals(ENTITY_NO, request.getEntityNo()),
            () -> assertEquals(List.of(candidate.channelId(), candidate.channelAccountId(), candidate.midId()),
                List.of(Long.valueOf(request.getChannelId()), Long.valueOf(request.getChannelAccountId()),
                    Long.valueOf(request.getMidId()))),
            () -> assertEquals(0, new BigDecimal(AMOUNT).compareTo(new BigDecimal(request.getAmount()))),
            () -> assertEquals(CURRENCY, request.getCriteria().getCurrency()),
            () -> assertFalse(input.has("channelAccount"), "Payment 不得发送账号材料"),
            () -> assertEquals(cardApi.tickets(), List.of(input.path("card").path("token").asString()), "卡字段句柄为票据"),
            () -> assertNotEquals(paid.token, input.path("card").path("token").asString(), "不再传支付工具 Token"),
            () -> assertEquals(List.of(String.valueOf(attemptId(response))),
                cardApi.ticketRequests().stream().map(ticket -> ticket.getPaymentAttemptId()).toList()),
            () -> assertEquals(String.valueOf(mapper.actions(attemptId(response)).get(1).get("actionId")),
                cardApi.ticketRequests().getFirst().getPaymentActionId(), "票据绑定当前 PAY 动作"),
            () -> assertEquals(mapper.instrumentTokens().getFirst().get("instrumentRef"),
                cardApi.ticketRequests().getFirst().getInstrumentRef()),
            () -> assertEquals("10.00", input.path("payment").path("amount").path("value").asString()),
            () -> assertEquals(COUNTRY_CODE, input.path("payment").path("country").asString()),
            () -> assertEquals(RETURN_URL, input.path("payment").path("returnUrl").asString()),
            () -> assertEquals(CONSUMER_IP, input.path("payment").path("clientIp").asString()),
            () -> assertFalse(input.toString().contains(PAN), "Platform 输入包含卡号"),
            () -> assertEquals("1111/true", call.revealed(), "读卡取得卡号与 CVV"),
            () -> assertEquals(List.of(String.valueOf(attemptId(response))), risk.attempts()));
        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), mapper.attempt(attemptId).get("status")),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), mapper.intent(paid.intentId).get("status")),
            () -> assertEquals(List.of(ActionType.PAY_RISK.getCode(), ActionType.PAY.getCode()),
                mapper.actions(attemptId).stream().map(row -> row.get("type")).toList()),
            () -> assertEquals(ActionStatus.SUCCEEDED.getCode(), mapper.actions(attemptId).get(1).get("status")),
            () -> assertEquals("T-PMX-1", mapper.transactionRefs().getFirst().get("externalId")),
            () -> assertEquals(candidate.midId(), ((Number)mapper.attempt(attemptId).get("midId")).longValue()));
    }

    /** 3DS：返回 202 与临时交互指令；PAY 动作处理中并记 3DS 交互类型，跳转地址不落库；渠道交易引用已保存。 */
    @Test
    void test_threeDsChallenge() {
        channel.pending("T-PMX-3DS", REDIRECT_URL);

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        @SuppressWarnings("unchecked")
        var nextAction = (Map<String, Object>)response.getBody().get("nextAction");
        var pay = mapper.actions(attemptId).get(1);
        assertAll(() -> assertEquals(202, response.getStatusCode().value()),
            () -> assertEquals(AttemptStatus.PROCESSING.getCode(), response.getBody().get("status")),
            () -> assertEquals(InteractionType.THREE_DS_CHALLENGE.getCode(), nextAction.get("type")),
            () -> assertEquals(REDIRECT_URL, nextAction.get("redirectUrl")),
            () -> assertNotNull(nextAction.get("expiresAt")),
            () -> assertEquals(ActionStatus.PROCESSING.getCode(), pay.get("status")),
            () -> assertEquals(InteractionType.THREE_DS_CHALLENGE.getCode(), pay.get("interactionType")),
            () -> assertEquals("T-PMX-3DS", mapper.transactionRefs().getFirst().get("externalId")),
            () -> assertFalse(mapper.executionText().contains("transient-3ds"), "跳转地址落库"));
    }

    /** 发卡行拒绝：Attempt 失败、Intent 释放活动尝试，记录渠道失败码；已创建的渠道交易仍保存引用。 */
    @Test
    void test_declined() {
        channel.declined("T-PMX-D", "ISSUER_PAYMENT_REJECTED", "Transaction failed at issuer end.");
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        var pay = mapper.actions(attemptId).get(1);
        assertAll(() -> assertEquals(200, response.getStatusCode().value()),
            () -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals(ActionStatus.FAILED.getCode(), pay.get("status")),
            () -> assertEquals("ISSUER_PAYMENT_REJECTED", pay.get("code")),
            () -> assertNull(mapper.intent(paid.intentId).get("activeAttemptId")),
            () -> assertEquals(IntentStatus.OPEN.getCode(), mapper.intent(paid.intentId).get("status")));
    }

    /** 渠道结果未知（channel-connector 映射的 ERROR）：PAY 动作 UNKNOWN，Attempt 保持处理中，不重发扣款。 */
    @Test
    void test_channelResultUnknown() {
        channel.unknown();

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(202, response.getStatusCode().value()),
            () -> assertEquals(ActionStatus.UNKNOWN.getCode(), mapper.actions(attemptId).get(1).get("status")),
            () -> assertEquals(AttemptStatus.PROCESSING.getCode(), mapper.attempt(attemptId).get("status")),
            () -> assertEquals(1, channel.calls().size()));
    }

    /** 保证未发出请求（channel-connector 映射的 NOT_SENT，原因为 Platform 初始化失败）：PAY 动作明确失败并记录原因。 */
    @Test
    void test_platformNotSent() {
        channel.notSent(ChannelConnectorMocker.READ_CARD_REJECTED);

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals("INTEGRATION_INITIALIZATION_FAILED", mapper.actions(attemptId).get(1).get("code")));
    }

    /** 原提交重放：返回原终态，不再评估风险或调用渠道。 */
    @Test
    void test_replayDoesNotExecuteAgain() {
        channel.succeeded("T-PMX-R");
        var paid = newPayment();
        paid.pay();

        var replay = paid.pay();

        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), replay.getBody().get("status")),
            () -> assertEquals(1, channel.calls().size()), () -> assertEquals(1, risk.attempts().size()));
    }

    /** 读卡票据只能使用一次：Platform 已凭票据读卡后，再次使用同一票据被 card 拒绝且不返回卡数据。 */
    @Test
    void test_revealRules() {
        channel.succeeded("T-PMX-V");
        newPayment().pay();

        String ticket = cardApi.tickets().getFirst();
        assertAll(() -> assertEquals("1111/true", channel.only().revealed()),
            () -> assertArrayEquals(new String[] {"REJECTED 400", null}, platform.reveal(ticket), "票据二次使用"),
            () -> assertEquals(List.of(ticket, ticket), cardApi.reveals()));
    }

    /** card 拒绝签发票据（CVV 已领取或工具已过期）：PAY 动作以 CARD_MATERIAL_UNAVAILABLE 明确失败，渠道没有收到请求。 */
    @Test
    void test_ticketRefusedNotSent() {
        cardApi.rejectTickets(CardApiMocker.fault(400, CommonErrorCode.BUSINESS_CHECK_FAILED));
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals(PayActionActor.CARD_MATERIAL_UNAVAILABLE, mapper.actions(attemptId).get(1).get("code")),
            () -> assertTrue(channel.calls().isEmpty(), "未取得票据时不得调用渠道"),
            () -> assertEquals(1, cardApi.ticketRequests().size()));
    }
}
