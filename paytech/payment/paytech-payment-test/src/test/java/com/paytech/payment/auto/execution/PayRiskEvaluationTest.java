package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.paytech.card.api.enums.CardBrand;
import com.paytech.card.api.enums.CardFundingType;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.action.PayActionActor;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.CaptureMode;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.CardApiMocker;
import com.paytech.payment.test.mocker.ChannelConnectorMocker.Candidate;
import com.paytech.payment.test.mocker.RiskMocker;
import com.paytech.payment.test.mocker.RiskMocker.Reply;
import com.paytech.risk.decision.api.enums.RiskDecisionEvaluationErrorCode;
import com.paytech.risk.decision.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.decision.api.evaluation.response.EvaluateDecisionResponseBody;

/**
 * 支付风控（需求 20261015 payment-接入Risk统一评估）：从 pay 的真实 HTTP 入口经 PAY_RISK 动作，以真实 RiskDecisionApi 客户端调用
 * Risk 测试 HTTP 服务。
 *
 * <p>
 * 覆盖：统一请求的 17 个输入字段及 BIN 库卡属性（发卡国、卡组织、借贷记，未识别时省略）；ALLOW 的后继分支（3DS 全链路起要求认证也进入 PAY）；REVIEW/DENY/FAILED/TIMED_OUT 使 Attempt 失败并释放 Intent；400/422/无效响应
 * 不重试；409、503 与读超时用同一请求重试一次；受限值不进日志。消费者引用、IP、UA 与卡号均为合成值。
 *
 * @see PaymentIntentApi#pay(String, String, com.paytech.payment.api.execution.request.PayRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class PayRiskEvaluationTest extends AbstractPayCardTest {
    private static final String BIN = "41111111";
    private static final String ISSUER_REGION = "GCC";
    private static final Duration BEYOND_READ_TIMEOUT = Duration.ofMillis(1500);

    private Candidate candidate;

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选；风控与渠道结论按用例配置。 */
    @BeforeEach
    void setUpClassMocks() {
        candidate = channel.candidate(ENTITY_NO, CURRENCY);
    }

    /** 放行且无需认证（自动捕获）：请求合同与 17 个输入字段正确，保存评估结果并进入 PAY。 */
    @Test
    void test_allowWithoutAuthentication() {
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        platformSucceeds();
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        var riskAction = mapper.actions(attemptId).getFirst();
        var request = risk.requests().getFirst();
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), response.getBody().get("status")),
            () -> assertEquals(PaymentAttemptRiskInput.POINT_CODE, request.getPointCode()),
            () -> assertEquals(PaymentAttemptRiskInput.CONTRACT_VERSION, request.getContractVersion()),
            () -> assertEquals(String.valueOf(attemptId), request.getBusinessKey()),
            () -> assertEquals(expectedInputs(paid, riskAction.get("actionId"), CaptureMode.AUTOMATIC),
                withoutFingerprint(request.getInputs())),
            () -> assertTrue(request.getInputs().get("cardFingerprint").matches("[0-9a-f]{64}"), "卡指纹格式"),
            () -> assertEquals(actionTypes(ActionType.PAY_RISK, ActionType.PAY), types(attemptId)));
        assertRiskSaved(riskAction, RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /**
     * 识别的 BIN（BIN 库需求 AC-08）：评估请求在 17 个字段之外带上 Token 冻结的发卡国、卡组织与借贷记类型；号段未提供的卡等级等不传 Risk。
     * 未识别时三个字段省略，见 {@link #test_allowWithoutAuthentication()}。
     */
    @Test
    void test_cardAttributesInRiskInput() {
        cardApi.binRange(BIN, CardApiMocker.attributes(CardBrand.VISA.getCode(), CardFundingType.DEBIT.getCode(), null,
            null, COUNTRY_CODE, ISSUER_REGION));
        risk.respond(RiskResult.DENY, null);
        var paid = newPayment();

        long attemptId = attemptId(paid.pay());

        var expected = expectedInputs(paid, mapper.actions(attemptId).getFirst().get("actionId"), CaptureMode.AUTOMATIC);
        expected.put("issuerCountryCode", COUNTRY_CODE);
        expected.put("cardBrand", CardBrand.VISA.getCode());
        expected.put("fundingType", CardFundingType.DEBIT.getCode());
        assertEquals(expected, withoutFingerprint(risk.requests().getFirst().getInputs()));
    }

    /** 同一张卡在不同 Intent 的支付尝试中指纹相同，且不包含卡号（风控拒绝，不进入渠道）。 */
    @Test
    void test_cardFingerprintStableAcrossAttempts() {
        risk.respond(RiskResult.DENY, null);

        newPayment().pay();
        newPayment().pay();

        var fingerprints = risk.requests().stream().map(body -> body.getInputs().get("cardFingerprint")).toList();
        assertAll(() -> assertEquals(2, fingerprints.size()),
            () -> assertEquals(fingerprints.get(0), fingerprints.get(1)),
            () -> assertFalse(fingerprints.getFirst().contains(PAN), "指纹包含卡号"));
    }

    /** 放行且无需认证（手动捕获）：进入 AUTHORIZE。 */
    @Test
    void test_allowManualCapture() {
        channel.supports(candidate, ChannelOperation.AUTHORIZE);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        var paid = newPayment();

        long attemptId = attemptId(paid.pay(CaptureMode.MANUAL));

        assertAll(() -> assertEquals(actionTypes(ActionType.PAY_RISK, ActionType.AUTHORIZE), types(attemptId)),
            () -> assertEquals(CaptureMode.MANUAL.getCode(), risk.requests().getFirst().getInputs().get("captureMode")),
            () -> assertTrue(channel.calls().isEmpty()));
    }

    /**
     * 放行且要求 3DS（3DS 全链路 AC-01、AC-02）：保存认证要求后进入 PAY，不再进入 AUTHENTICATE；本类候选未声明 3DS 模式，PAY 不发出
     * 渠道请求，以 THREE_DS_CHANNEL_UNAVAILABLE 失败并释放 Intent，不会卡在 UNKNOWN。
     */
    @Test
    void test_allowWithThreeDs() {
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.THREE_DS_REQUIRED);
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        var riskAction = mapper.actions(attemptId).getFirst();
        assertAll(() -> assertEquals(actionTypes(ActionType.PAY_RISK, ActionType.PAY), types(attemptId)),
            () -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals(PayActionActor.THREE_DS_CHANNEL_UNAVAILABLE, mapper.actions(attemptId).get(1).get("code")),
            () -> assertNull(mapper.intent(paid.intentId).get("activeAttemptId")),
            () -> assertTrue(channel.calls().isEmpty()));
        assertRiskSaved(riskAction, RiskResult.ALLOW, AuthenticationRequirement.THREE_DS_REQUIRED);
    }

    /** 拦截或 Risk 技术终态：保存结论，Attempt 失败、Intent 释放，可用新提交再次支付。 */
    @ParameterizedTest
    @EnumSource(value = RiskResult.class, names = {"REVIEW", "DENY", "FAILED", "TIMED_OUT"})
    void test_notAllowed(RiskResult result) {
        risk.respond(result, null);
        var paid = newPayment();

        var response = paid.pay();

        long attemptId = attemptId(response);
        var riskAction = mapper.actions(attemptId).getFirst();
        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals(actionTypes(ActionType.PAY_RISK), types(attemptId)),
            () -> assertEquals(AttemptStatus.FAILED.getCode(), mapper.attempt(attemptId).get("status")),
            () -> assertNull(mapper.attempt(attemptId).get("activeActionId")), () -> assertIntentReleased(paid),
            () -> assertTrue(channel.calls().isEmpty()));
        assertRiskSaved(riskAction, result, null);

        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        platformSucceeds();
        assertEquals(AttemptStatus.SUCCEEDED.getCode(), paid.retry().pay().getBody().get("status"));
    }

    /** 400、422 与无效结论属于对接缺陷：只调用一次，不重试，失败关闭。 */
    @ParameterizedTest
    @MethodSource("definiteFailures")
    void test_definiteFailureWithoutRetry(Reply reply) {
        risk.replies(reply);
        var paid = newPayment();

        var response = paid.pay();

        assertFailedClosed(paid, response, 1);
    }

    /** 409、503（含 Risk 未配置）与读超时：用同一请求重试一次，仍失败则失败关闭，风控动作不为 UNKNOWN。 */
    @ParameterizedTest
    @MethodSource("retryableFailures")
    void test_retryableFailureTwice(Reply first, Reply second) {
        risk.replies(first, second);
        var paid = newPayment();

        var response = paid.pay();

        assertFailedClosed(paid, response, 2);
        assertSameRequests();
    }

    /** 409、503 与读超时后重试取得放行：继续进入 PAY 并成功。 */
    @ParameterizedTest
    @MethodSource("retryableFirstFailures")
    void test_retryableFailureThenAllow(Reply first) {
        risk.replies(first, RiskMocker.result(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED));
        platformSucceeds();

        var response = newPayment().pay();

        long attemptId = attemptId(response);
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), response.getBody().get("status")),
            () -> assertEquals(2, risk.requests().size()),
            () -> assertEquals(actionTypes(ActionType.PAY_RISK, ActionType.PAY), types(attemptId)));
        assertSameRequests();
        assertRiskSaved(mapper.actions(attemptId).getFirst(), RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 受限值（消费者引用、IP、User-Agent、卡指纹）在成功与失败路径中都不进入日志。 */
    @Test
    void test_restrictedValuesNotLogged(CapturedOutput output) {
        risk.replies(RiskMocker.problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR.getCode()));
        newPayment().pay();
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
        platformSucceeds();
        newPayment().pay();

        String fingerprint = risk.requests().getFirst().getInputs().get("cardFingerprint");
        String logs = output.getAll();
        assertAll(() -> assertTrue(logs.contains("支付风控未取得结论"), "失败路径应有告警日志"),
            () -> assertFalse(logs.contains(CONSUMER_REFERENCE), "日志包含消费者引用"),
            () -> assertFalse(logs.contains(CONSUMER_IP), "日志包含 IP"),
            () -> assertFalse(logs.contains(CONSUMER_USER_AGENT), "日志包含 User-Agent"),
            () -> assertFalse(logs.contains(fingerprint), "日志包含卡指纹"));
    }

    /** 不重试的确定失败：400、422、放行缺认证要求、未知结论、评估 ID 非法。 */
    static Stream<Reply> definiteFailures() {
        return Stream.of(RiskMocker.problem(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST.getCode()),
            RiskMocker.problem(HttpStatus.UNPROCESSABLE_CONTENT, CommonErrorCode.VALIDATION_ERROR.getCode()),
            RiskMocker.body(response(RiskMocker.EVALUATION_ID, RiskResult.ALLOW.getCode(), Map.of())),
            RiskMocker.body(response(RiskMocker.EVALUATION_ID, "UNRECOGNIZED", Map.of())),
            RiskMocker.body(response("0", RiskResult.DENY.getCode(), Map.of())));
    }

    /** 重试一次的失败：409、503 未配置与读超时各自连续两次。 */
    static Stream<Arguments> retryableFailures() {
        return retryableFirstFailures().map(first -> Arguments.of(first, first));
    }

    /** 首次可重试的失败。 */
    static Stream<Reply> retryableFirstFailures() {
        return Stream.of(RiskMocker.problem(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode()),
            RiskMocker.problem(HttpStatus.SERVICE_UNAVAILABLE,
                RiskDecisionEvaluationErrorCode.RISK_DECISION_NOT_CONFIGURED.getCode()),
            RiskMocker.delayed(BEYOND_READ_TIMEOUT,
                RiskMocker.result(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED)));
    }

    /** 原样构造 Risk 响应 DTO。 */
    static EvaluateDecisionResponseBody response(String evaluationId, String result, Map<String, String> outputs) {
        var body = new EvaluateDecisionResponseBody();
        body.setEvaluationId(evaluationId);
        body.setResult(result);
        body.setOutputs(outputs);
        return body;
    }

    /** 失败关闭：Attempt 失败、Intent 释放，风控动作失败且无结论；没有执行渠道操作。 */
    void assertFailedClosed(Payment paid, ResponseEntity<Map<String, Object>> response, int riskCalls) {
        long attemptId = attemptId(response);
        var riskAction = mapper.actions(attemptId).getFirst();
        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), response.getBody().get("status")),
            () -> assertEquals(riskCalls, risk.requests().size()),
            () -> assertEquals(actionTypes(ActionType.PAY_RISK), types(attemptId)),
            () -> assertEquals(ActionStatus.FAILED.getCode(), riskAction.get("status")),
            () -> assertNull(riskAction.get("riskResult")), () -> assertNull(riskAction.get("evaluationId")),
            () -> assertNull(riskAction.get("code")), () -> assertIntentReleased(paid),
            () -> assertTrue(channel.calls().isEmpty()));
    }

    /** 重试使用完全相同的请求。 */
    void assertSameRequests() {
        var requests = risk.requests();
        assertAll(() -> assertEquals(2, requests.size()),
            () -> assertEquals(requests.get(0).getBusinessKey(), requests.get(1).getBusinessKey()),
            () -> assertEquals(requests.get(0).getInputs(), requests.get(1).getInputs()));
    }

    /** 风控动作成功并保存评估 ID、结论与认证要求。 */
    static void assertRiskSaved(Map<String, Object> riskAction, RiskResult result,
        AuthenticationRequirement authentication) {
        assertAll(() -> assertEquals(ActionStatus.SUCCEEDED.getCode(), riskAction.get("status")),
            () -> assertEquals(RiskMocker.EVALUATION_ID, String.valueOf(riskAction.get("evaluationId"))),
            () -> assertEquals(result.getCode(), riskAction.get("riskResult")),
            () -> assertEquals(authentication == null ? null : authentication.getCode(), riskAction.get("authentication")));
    }

    /** Intent 仍开放且没有活动支付尝试。 */
    void assertIntentReleased(Payment paid) {
        var intent = mapper.intent(paid.intentId);
        assertAll(() -> assertEquals(IntentStatus.OPEN.getCode(), intent.get("status")),
            () -> assertNull(intent.get("activeAttemptId")));
    }

    /** 按用例约定的 17 个输入字段（不含卡指纹，指纹另行检查格式与稳定性）。 */
    Map<String, String> expectedInputs(Payment paid, Object riskActionId, CaptureMode captureMode) {
        var inputs = new LinkedHashMap<String, String>();
        inputs.put("paymentIntentId", String.valueOf(paid.intentId));
        inputs.put("riskActionId", String.valueOf(riskActionId));
        inputs.put("orderId", String.valueOf(paid.submissionId));
        inputs.put("clientId", CLIENT_ID);
        inputs.put("entityNo", ENTITY_NO);
        inputs.put("paymentMethodCode", PAYMENT_METHOD_CODE);
        inputs.put("captureMode", captureMode.getCode());
        inputs.put("amount", "10");
        inputs.put("currency", CURRENCY);
        inputs.put("industryCode", INDUSTRY_CODE);
        inputs.put("mccCode", MCC_CODE);
        inputs.put("consumerCountryCode", COUNTRY_CODE);
        inputs.put("consumerReference", CONSUMER_REFERENCE);
        inputs.put("clientIpAddress", CONSUMER_IP);
        inputs.put("userAgent", CONSUMER_USER_AGENT);
        inputs.put("cardBin", BIN);
        return inputs;
    }

    /** 去掉卡指纹后的输入，用于整体比较。 */
    static Map<String, String> withoutFingerprint(Map<String, String> inputs) {
        var copy = new LinkedHashMap<>(inputs);
        assertNotNull(copy.remove("cardFingerprint"), "缺少卡指纹");
        return copy;
    }

    /** 渠道 PAY 成功。 */
    void platformSucceeds() {
        channel.succeeded("T-PMX-RISK");
    }

    /** 支付尝试的动作类型序列。 */
    List<String> types(long attemptId) {
        return mapper.actions(attemptId).stream().map(row -> (String)row.get("type")).toList();
    }

    /** 期望的动作类型序列。 */
    static List<String> actionTypes(ActionType... types) {
        return Stream.of(types).map(ActionType::getCode).toList();
    }
}
