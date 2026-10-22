package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.core.enums.TerminationDecision;
import com.paytech.payment.execution.core.enums.TerminationReason;
import com.paytech.payment.test.base.AbstractPayResultTest;

/**
 * 终止支付意图：经真实 HTTP 入口，按 Intent 与活动尝试的当前事实给出五种决定。
 *
 * <p>
 * 覆盖：无尝试终止且终态随原因为 CANCELLED / EXPIRED、已终态不改写、有活动尝试处理中、已授权未捕获、已收款、不存在、其他 Client、非法原因。
 *
 * @see PaymentIntentApi#terminateIntent(String, com.paytech.payment.api.execution.request.TerminateIntentRequestBody)
 */
class TerminateIntentTest extends AbstractPayResultTest {
    /** 无尝试、取消订单：本次终止，Intent 进入 CANCELLED。 */
    @Test
    void test_noAttemptTerminated() {
        assertTerminatedAs(TerminationReason.ORDER_CANCELLED, IntentStatus.CANCELLED);
    }

    /** 无尝试、经营主体关闭：本次终止，Intent 进入 CANCELLED。 */
    @Test
    void test_operatingEntityClosedCancelled() {
        assertTerminatedAs(TerminationReason.OPERATING_ENTITY_CLOSED, IntentStatus.CANCELLED);
    }

    /** 无尝试、订单到期：本次终止，Intent 进入 EXPIRED。 */
    @Test
    void test_orderExpiredExpired() {
        assertTerminatedAs(TerminationReason.ORDER_EXPIRED, IntentStatus.EXPIRED);
    }

    /** 已取消：再以到期原因终止返回 ALREADY_TERMINATED，仍为 CANCELLED，版本不变。 */
    @Test
    void test_alreadyTerminatedNotRewritten() {
        var intent = newIntent();
        terminate(intent.intentId, intent.clientId, TerminationReason.ORDER_CANCELLED.getCode());
        var before = mapper.intent(intent.intentId);

        var body = terminate(intent.intentId, intent.clientId, TerminationReason.ORDER_EXPIRED.getCode()).getBody();

        assertAll(() -> assertEquals(TerminationDecision.ALREADY_TERMINATED.getCode(), body.get("decision")),
            () -> assertEquals(IntentStatus.CANCELLED.getCode(), body.get("status")),
            () -> assertEquals(before, mapper.intent(intent.intentId)));
    }

    /** 已过期：再以取消原因终止返回 ALREADY_TERMINATED，仍为 EXPIRED，版本不变。 */
    @Test
    void test_alreadyExpiredNotRewritten() {
        var intent = newIntent();
        terminate(intent.intentId, intent.clientId, TerminationReason.ORDER_EXPIRED.getCode());
        var before = mapper.intent(intent.intentId);

        var body = terminate(intent.intentId, intent.clientId, TerminationReason.ORDER_CANCELLED.getCode()).getBody();

        assertAll(() -> assertEquals(TerminationDecision.ALREADY_TERMINATED.getCode(), body.get("decision")),
            () -> assertEquals(IntentStatus.EXPIRED.getCode(), body.get("status")),
            () -> assertEquals(before, mapper.intent(intent.intentId)));
    }

    /**
     * 活动尝试结果未知：PENDING，Intent 保持开放且活动尝试不变。
     * <li>PAY 结果未知时同事务登记 PAY_RESULT_QUERY，提交后立即执行并持有同一 Intent 锁；先等首轮结束再终止，避免锁冲突。</li>
     */
    @Test
    void test_activeAttemptPending() {
        prepareChannel();
        var pending = unknownPay();
        var paid = pending.payment();
        long attemptId = pending.attemptId();
        var before = mapper.intent(paid.intentId);

        var body = terminate(paid.intentId, paid.clientId, TerminationReason.ORDER_CANCELLED.getCode()).getBody();

        assertAll(() -> assertEquals(TerminationDecision.PENDING.getCode(), body.get("decision")),
            () -> assertEquals(IntentStatus.OPEN.getCode(), body.get("status")),
            () -> assertEquals(attemptId, ((Number)before.get("activeAttemptId")).longValue()),
            () -> assertEquals(before, mapper.intent(paid.intentId)));
    }

    /**
     * 已授权未捕获、无当前动作：REVERSAL_REQUIRED，不终止。
     * <li>先等查单恢复任务首轮结束（进入退避），再改夹具并终止，避免与其 Intent 锁和尝试写入竞争。</li>
     */
    @Test
    void test_authorizedReversalRequired() {
        prepareChannel();
        var pending = unknownPay();
        var paid = pending.payment();
        long attemptId = pending.attemptId();
        data.authorizedAwaitingCapture(attemptId, new BigDecimal(AMOUNT));
        var before = mapper.intent(paid.intentId);

        var body = terminate(paid.intentId, paid.clientId, TerminationReason.ORDER_CANCELLED.getCode()).getBody();

        assertAll(() -> assertEquals(TerminationDecision.REVERSAL_REQUIRED.getCode(), body.get("decision")),
            () -> assertEquals(IntentStatus.OPEN.getCode(), body.get("status")),
            () -> assertEquals(before, mapper.intent(paid.intentId)));
    }

    /** 已收款：ALREADY_PAID，Intent 保持 SUCCEEDED。 */
    @Test
    void test_alreadyPaid() {
        prepareChannel();
        channel.succeeded("T-TERM-PAID");
        var paid = newPayment();
        paid.pay();
        var before = mapper.intent(paid.intentId);

        var body = terminate(paid.intentId, paid.clientId, TerminationReason.ORDER_CANCELLED.getCode()).getBody();

        assertAll(() -> assertEquals(TerminationDecision.ALREADY_PAID.getCode(), body.get("decision")),
            () -> assertEquals(IntentStatus.SUCCEEDED.getCode(), body.get("status")),
            () -> assertEquals(before, mapper.intent(paid.intentId)));
    }

    /** 不存在的 Intent：404 NOT_FOUND。 */
    @Test
    void test_notFound() {
        var failure = terminateFailure(Long.MAX_VALUE, CLIENT_ID, TerminationReason.ORDER_CANCELLED.getCode());

        assertAll(() -> assertEquals(404, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.NOT_FOUND.getCode(), problemCode(failure)));
    }

    /** 其他 Client：拒绝且不改写 Intent。 */
    @Test
    void test_otherClientRejected() {
        var intent = newIntent();
        var before = mapper.intent(intent.intentId);

        var failure = terminateFailure(intent.intentId, "102", TerminationReason.ORDER_CANCELLED.getCode());

        assertAll(() -> assertEquals(400, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), problemCode(failure)),
            () -> assertEquals(before, mapper.intent(intent.intentId)));
    }

    /** 未登记的原因：422 字段校验失败（公共 Problem 对 Body 字段错误的映射），不改写 Intent。 */
    @Test
    void test_invalidReason() {
        var intent = newIntent();
        var before = mapper.intent(intent.intentId);

        var failure = terminateFailure(intent.intentId, intent.clientId, "ORDER_REFUNDED");

        assertAll(() -> assertEquals(422, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), problemCode(failure)),
            () -> assertEquals(before, mapper.intent(intent.intentId)));
    }

    /** 无尝试的 Intent 以指定原因终止：TERMINATED，响应与库中均为期望终态，版本加一。 */
    void assertTerminatedAs(TerminationReason reason, IntentStatus expected) {
        var intent = newIntent();
        long version = ((Number)mapper.intent(intent.intentId).get("version")).longValue();

        var body = terminate(intent.intentId, intent.clientId, reason.getCode()).getBody();

        var row = mapper.intent(intent.intentId);
        assertAll(() -> assertEquals(String.valueOf(intent.intentId), body.get("intentId")),
            () -> assertEquals(TerminationDecision.TERMINATED.getCode(), body.get("decision")),
            () -> assertEquals(expected.getCode(), body.get("status")),
            () -> assertEquals(expected.getCode(), row.get("status")),
            () -> assertEquals(version + 1, ((Number)row.get("version")).longValue()));
    }

    /** 需要提交 pay 的用例：AE001/AED 下一个支持 PAY 的 MID 候选与风控通过。 */
    void prepareChannel() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 经真实 HTTP 终止；4xx/5xx 使测试失败。 */
    ResponseEntity<Map<String, Object>> terminate(long intentId, String clientId, String reason) {
        var body = new LinkedHashMap<String, Object>();
        body.put("clientId", clientId);
        body.put("reason", reason);
        return client().post().uri("/payment-execution/v1/intents/{intentId}/terminate", intentId)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
            .toEntity(new ParameterizedTypeReference<>() {});
    }

    /** 终止并返回预期的 HTTP 错误；成功响应使测试失败。 */
    RestClientResponseException terminateFailure(long intentId, String clientId, String reason) {
        return assertThrows(RestClientResponseException.class, () -> terminate(intentId, clientId, reason));
    }

    /** Problem 响应体中的错误代码。 */
    static Object problemCode(RestClientResponseException failure) {
        return failure.getResponseBodyAs(Map.class).get("code");
    }
}
