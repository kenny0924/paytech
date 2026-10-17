package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.ChannelConnectorMocker;
import com.paytech.payment.test.mocker.ChannelConnectorMocker.Candidate;

/**
 * 卡直付执行链路（PayerMax 卡直付对接技术方案第 5 节）：从 pay 的真实 HTTP 入口经路由、受理事务、风控动作与 PAY 动作，
 * 以真实客户端调用 Risk 与 channel-connector 的测试 HTTP 服务（D06：channel-connector 返回已映射结论）。渠道操作含支付工具 Token 时，
 * 测试服务像真实 Platform 一样回调 Payment 读卡接口取得卡数据。
 *
 * <p>
 * 覆盖：成功、3DS 待认证（临时交互指令不落库）、发卡行拒绝、结果未知、保证未发出请求、原提交重放不再执行，
 * 以及读卡接口的调用方与执行状态约束。卡号为公开测试卡号，属合成数据。风控各结论与故障见 {@link PayRiskEvaluationTest}。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PayCardChannelTest extends AbstractPayCardTest {
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    private Candidate candidate;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        candidate = channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 成功：channel-connector 收到冻结三元组、OE 与支付工具 Token（无卡值、无账号材料），读卡取得卡号与一次性 CVV；Attempt、Intent 成功并保存渠道交易引用。 */
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
            () -> assertEquals(paid.token, input.path("card").path("token").asString()),
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

    /** 读卡接口：非 Platform 调用方 404；支付尝试不在执行资金动作（已成功）时 400，不返回卡数据。 */
    @Test
    void test_revealRules() {
        channel.succeeded("T-PMX-V");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        assertAll(() -> assertEquals(404, reveal("commerce-service", paid.token, attemptId)),
            () -> assertEquals(400, reveal("platform-support-service", paid.token, attemptId)));
    }

    /** 以给定调用方直接调用读卡接口，返回 HTTP 状态。 */
    int reveal(String caller, String token, long attemptId) {
        try {
            return client().post().uri("/payment-card/v1/channel-card-materials/reveal").header("X-Caller-Service", caller)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentInstrumentToken", token, "paymentAttemptId",
                    String.valueOf(attemptId), "purpose", "CHANNEL_EXECUTION"))
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }
}
