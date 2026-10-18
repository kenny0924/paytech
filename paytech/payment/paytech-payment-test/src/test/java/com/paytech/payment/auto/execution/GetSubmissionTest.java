package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.InteractionType;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;

/**
 * EX-I06 按来源提交查询原支付结果：经真实 pay 入口形成各状态的支付尝试，再经真实 HTTP 查询。
 *
 * <p>
 * 覆盖：成功、失败（失败代码）、处理中、3DS 待操作（返回交互但不返回跳转地址）；只申请工具没有尝试、跨 Client、跨 Intent 均为 404。
 *
 * @see PaymentIntentApi#getSubmission(String, String, String)
 */
class GetSubmissionTest extends AbstractPayCardTest {
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    /** 本类公共：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 成功：返回尝试身份、支付方式、成功状态与按币种精度的已捕获金额，无交互与失败代码。 */
    @Test
    void test_succeeded() {
        channel.succeeded("T-Q-1");
        var paid = newPayment();
        long attemptId = attemptId(paid.pay());

        var body = getSubmission(paid.intentId, paid.submissionId, paid.clientId).getBody();

        assertAll(() -> assertEquals(String.valueOf(paid.submissionId), body.get("sourceSubmissionId")),
            () -> assertEquals(String.valueOf(paid.intentId), body.get("intentId")),
            () -> assertEquals(String.valueOf(attemptId), body.get("attemptId")),
            () -> assertEquals(PAYMENT_METHOD_CODE, body.get("paymentMethodCode")),
            () -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), body.get("status")),
            () -> assertEquals(AMOUNT, body.get("capturedAmount")), () -> assertEquals(CURRENCY, body.get("currency")),
            () -> assertNull(body.get("interaction")), () -> assertNull(body.get("failureCode")),
            () -> assertNotNull(Instant.parse((String)body.get("observedAt"))));
    }

    /** 失败：返回最后动作的渠道失败代码，已捕获金额为零。 */
    @Test
    void test_failed() {
        channel.declined("T-Q-D", "ISSUER_PAYMENT_REJECTED", "Transaction failed at issuer end.");
        var paid = newPayment();
        paid.pay();

        var body = getSubmission(paid.intentId, paid.submissionId, paid.clientId).getBody();

        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), body.get("status")),
            () -> assertEquals("ISSUER_PAYMENT_REJECTED", body.get("failureCode")),
            () -> assertEquals("0.00", body.get("capturedAmount")), () -> assertNull(body.get("interaction")));
    }

    /** 处理中：渠道结果未知时无交互、无失败代码。 */
    @Test
    void test_processing() {
        channel.unknown();
        var paid = newPayment();
        paid.pay();

        var body = getSubmission(paid.intentId, paid.submissionId, paid.clientId).getBody();

        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertNull(body.get("interaction")), () -> assertNull(body.get("failureCode")),
            () -> assertEquals("0.00", body.get("capturedAmount")));
    }

    /** 3DS 待操作：返回交互类型与 pay 响应相同的截止时间，不返回跳转地址。 */
    @Test
    void test_threeDsPending() {
        channel.pending("T-Q-3DS", REDIRECT_URL);
        var paid = newPayment();
        @SuppressWarnings("unchecked")
        var nextAction = (Map<String, Object>)paid.pay().getBody().get("nextAction");

        var response = getSubmission(paid.intentId, paid.submissionId, paid.clientId);

        var body = response.getBody();
        @SuppressWarnings("unchecked")
        var interaction = (Map<String, Object>)body.get("interaction");
        assertAll(() -> assertEquals(AttemptStatus.PROCESSING.getCode(), body.get("status")),
            () -> assertEquals(InteractionType.THREE_DS_CHALLENGE.getCode(), interaction.get("type")),
            () -> assertEquals(Instant.parse((String)nextAction.get("expiresAt")),
                Instant.parse((String)interaction.get("expiresAt"))),
            () -> assertEquals(2, interaction.size(), "交互只含类型与截止时间"),
            () -> assertFalse(body.toString().contains("transient-3ds"), "查询返回了跳转地址"));
    }

    /** 只申请了工具、没有支付尝试：404。 */
    @Test
    void test_instrumentOnlyNotFound() {
        var applied = newPayment();

        assertEquals(404, statusOf(applied.intentId, applied.submissionId, applied.clientId));
    }

    /** 其他 Client 查询：与不存在相同，404。 */
    @Test
    void test_otherClientNotFound() {
        channel.succeeded("T-Q-C");
        var paid = newPayment();
        paid.pay();

        assertEquals(404, statusOf(paid.intentId, paid.submissionId, "102"));
    }

    /** 提交属于另一个 Intent：404。 */
    @Test
    void test_otherIntentNotFound() {
        channel.succeeded("T-Q-I");
        var paid = newPayment();
        paid.pay();
        var other = newIntent();

        assertEquals(404, statusOf(other.intentId, paid.submissionId, paid.clientId));
    }

    /** 经真实 HTTP 查询；4xx/5xx 使测试失败。 */
    ResponseEntity<Map<String, Object>> getSubmission(long intentId, long submissionId, String clientId) {
        return client().get().uri("/payment-execution/v1/intents/{intentId}/submissions/{submissionId}?clientId={clientId}",
            intentId, submissionId, clientId).retrieve().toEntity(new ParameterizedTypeReference<>() {});
    }

    /** 查询并返回 HTTP 状态。 */
    int statusOf(long intentId, long submissionId, String clientId) {
        try {
            return getSubmission(intentId, submissionId, clientId).getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }
}
