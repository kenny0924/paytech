package com.paytech.payment.auto.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.payment.api.execution.PaymentIntentApi;
import com.paytech.payment.execution.core.enums.IntentStatus;
import com.paytech.payment.test.base.AbstractPayCardTest;

/**
 * 按来源业务查询支付意图：经真实 HTTP 入口读取 Intent 当前事实。
 *
 * <p>
 * 覆盖：命中、来源业务没有 Intent、其他 Client（均 404）、非法来源业务类型。
 *
 * @see PaymentIntentApi#getIntentBySource(String, String, String)
 */
class GetIntentBySourceTest extends AbstractPayCardTest {
    private static final String SOURCE_TYPE = "COMMERCE_ORDER";

    /** 命中：返回与按 ID 查询相同的意图事实。 */
    @Test
    void test_found() {
        var intent = newIntent();

        var response = getIntentBySource(SOURCE_TYPE, String.valueOf(intent.submissionId), intent.clientId);

        var body = response.getBody();
        assertAll(() -> assertEquals(200, response.getStatusCode().value()),
            () -> assertEquals(String.valueOf(intent.intentId), body.get("intentId")),
            () -> assertEquals(SOURCE_TYPE, body.get("sourceType")),
            () -> assertEquals(String.valueOf(intent.submissionId), body.get("sourceId")),
            () -> assertEquals(IntentStatus.OPEN.getCode(), body.get("status")),
            () -> assertNull(body.get("activeAttemptId")), () -> assertNull(body.get("successfulAttemptId")));
    }

    /** 来源业务没有支付意图：404 NOT_FOUND。 */
    @Test
    void test_notFound() {
        newIntent();

        var failure = failure(SOURCE_TYPE, String.valueOf(Long.MAX_VALUE), CLIENT_ID);

        assertAll(() -> assertEquals(404, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.NOT_FOUND.getCode(), problemCode(failure)));
    }

    /** 其他 Client 查询：与不存在相同，404 NOT_FOUND。 */
    @Test
    void test_otherClientNotFound() {
        var intent = newIntent();

        var failure = failure(SOURCE_TYPE, String.valueOf(intent.submissionId), "102");

        assertAll(() -> assertEquals(404, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.NOT_FOUND.getCode(), problemCode(failure)));
    }

    /** 非法来源业务类型（小写）：入口校验拒绝，不进入查询。 */
    @Test
    void test_invalidSourceType() {
        var intent = newIntent();

        var failure = failure("commerce_order", String.valueOf(intent.submissionId), intent.clientId);

        assertAll(() -> assertEquals(400, failure.getStatusCode().value()),
            () -> assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), problemCode(failure)));
    }

    /** 经真实 HTTP 按来源查询；4xx/5xx 使测试失败。 */
    ResponseEntity<Map<String, Object>> getIntentBySource(String sourceType, String sourceId, String clientId) {
        return client().get().uri("/payment-execution/v1/intents/by-source/{sourceType}/{sourceId}?clientId={clientId}",
            sourceType, sourceId, clientId).retrieve().toEntity(new ParameterizedTypeReference<>() {});
    }

    /** 查询并返回预期的 HTTP 错误；成功响应使测试失败。 */
    RestClientResponseException failure(String sourceType, String sourceId, String clientId) {
        return assertThrows(RestClientResponseException.class, () -> getIntentBySource(sourceType, sourceId, clientId));
    }

    /** Problem 响应体中的错误代码。 */
    static Object problemCode(RestClientResponseException failure) {
        return failure.getResponseBodyAs(Map.class).get("code");
    }
}
