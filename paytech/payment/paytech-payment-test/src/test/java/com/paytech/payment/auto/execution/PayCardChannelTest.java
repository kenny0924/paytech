package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.ChannelDataMocker.Fixture;

/**
 * 卡直付执行链路（PayerMax 卡直付对接技术方案第 5 节）：从 pay 的真实 HTTP 入口经路由、受理事务、风控动作与 PAY 动作，
 * 以真实客户端调用 Risk 与 Platform 的测试 HTTP 服务。Platform 测试服务像真实 Platform 一样回调 Payment 读卡接口取得卡数据。
 *
 * <p>
 * 覆盖：成功、3DS 待认证（临时交互指令不落库）、发卡行拒绝、结果未知、Platform 保证未发出请求、原提交重放不再执行，
 * 以及读卡接口的调用方与执行状态约束。卡号为公开测试卡号，属合成数据。风控各结论与故障见 {@link PayRiskEvaluationTest}。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
class PayCardChannelTest extends AbstractPayCardTest {
    private static final String CHANNEL_CODE = "PAYERMAX";
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    private Fixture fixture;

    /** 本类公共：PAYERMAX 渠道三元组（AE001、AED、CARD PAY 能力）与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.cardFixture(CHANNEL_CODE, ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 成功：Platform 收到冻结账号身份与支付工具 Token（无卡值），读卡取得卡号与一次性 CVV；Attempt、Intent 成功并保存渠道交易引用。 */
    @Test
    void test_paySucceeded() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-PMX-1","channelStatus":"SUCCESS"}""");
        var paid = newPayment();

        var response = paid.pay();

        var call = platform.only();
        var input = call.input();
        assertAll(() -> assertEquals(200, response.getStatusCode().value()),
            () -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), response.getBody().get("status")),
            () -> assertNull(response.getBody().get("nextAction")),
            () -> assertEquals("PAY", call.standardApiCode()), () -> assertEquals(CHANNEL_CODE, call.integrationCode()),
            () -> assertEquals(fixture.getAccount().getAccountVaultToken(),
                input.path("channelAccount").path("accountVaultToken").asString()),
            () -> assertEquals(ENTITY_NO, input.path("channelAccount").path("entityNo").asString()),
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
            () -> assertEquals(fixture.getMid().getMidId(), ((Number)mapper.attempt(attemptId).get("midId")).longValue()));
    }

    /** 3DS：返回 202 与临时交互指令；PAY 动作处理中并记 3DS 交互类型，跳转地址不落库；渠道交易引用已保存。 */
    @Test
    void test_threeDsChallenge() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"PENDING","nextAction":{"type":"THREE_DS_CHALLENGE","redirectUrl":"%s"},
             "channelTransactionRef":"T-PMX-3DS","channelStatus":"PENDING"}""".formatted(REDIRECT_URL));

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
        platform.respond("SUCCESS", """
            {"paymentStatus":"FAILED","channelTransactionRef":"T-PMX-D","channelStatus":"FAILED",
             "failureCode":"ISSUER_PAYMENT_REJECTED","failureMessage":"Transaction failed at issuer end."}""");
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

    /** Platform 未知错误（500 非保证码）：结果未知，PAY 动作 UNKNOWN，Attempt 保持处理中，不重发扣款。 */
    @Test
    void test_channelResultUnknown() {
        platform.fail(MockHttpFault.json(500, Map.of("type", "about:blank", "title", "Internal Server Error",
            "detail", "系统处理结果未知", "code", "INTERNAL_ERROR")));

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(202, response.getStatusCode().value()),
            () -> assertEquals(ActionStatus.UNKNOWN.getCode(), mapper.actions(attemptId).get(1).get("status")),
            () -> assertEquals(AttemptStatus.PROCESSING.getCode(), mapper.attempt(attemptId).get("status")),
            () -> assertEquals(1, platform.calls().size()));
    }

    /** Platform 保证未发出请求（503 初始化失败）：PAY 动作明确失败并记录原因。 */
    @Test
    void test_platformNotSent() {
        platform.fail(MockHttpFault.json(503, Map.of("type", "about:blank", "title", "Service Unavailable",
            "detail", "外部 API 初始化失败", "code", "INTEGRATION_INITIALIZATION_FAILED")));

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals("INTEGRATION_INITIALIZATION_FAILED", mapper.actions(attemptId).get(1).get("code")));
    }

    /** 原提交重放：返回原终态，不再评估风险或调用渠道。 */
    @Test
    void test_replayDoesNotExecuteAgain() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-PMX-R","channelStatus":"SUCCESS"}""");
        var paid = newPayment();
        paid.pay();

        var replay = paid.pay();

        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), replay.getBody().get("status")),
            () -> assertEquals(1, platform.calls().size()), () -> assertEquals(1, risk.attempts().size()));
    }

    /** 读卡接口：非 Platform 调用方 404；支付尝试不在执行资金动作（已成功）时 400，不返回卡数据。 */
    @Test
    void test_revealRules() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-PMX-V","channelStatus":"SUCCESS"}""");
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
