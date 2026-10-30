package com.paytech.payout.test.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** payout HTTP 响应断言：复用公共状态、Request ID 与 Problem 检查，补充字段读取与业务错误码断言。 */
public final class PayoutResponseChecker extends AbstractHttpResponseChecker<PayoutResponseChecker> {
    private final String raw;

    /** 以交换结果构造。 */
    public PayoutResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        byte[] bytes = result.getResponseBody();
        this.raw = bytes == null ? "" : new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected PayoutResponseChecker self() {
        return this;
    }

    /** 断言成功状态、JSON 媒体类型与 Request ID。 */
    public PayoutResponseChecker assertSuccess(int status) {
        return assertStatus(status).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    /** 断言 payout 合同错误码。 */
    public PayoutResponseChecker assertError(int status, CodeEnum code) {
        return assertProblem(status, code.getCode());
    }

    /** 断言 400 业务拒绝。 */
    public PayoutResponseChecker assertBusinessRejected() {
        return assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
    }

    /** 断言 400 参数错误。 */
    public PayoutResponseChecker assertInvalidRequest() {
        return assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 断言 422 请求体字段校验失败。 */
    public PayoutResponseChecker assertValidationError() {
        return assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode());
    }

    /** 断言 409 并发冲突（版本或摘要不符）。 */
    public PayoutResponseChecker assertConcurrentRequest() {
        return assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
    }

    /** 断言 Problem detail 包含片段。 */
    public PayoutResponseChecker assertDetailContains(String fragment) {
        String detail = body().path("detail").asString();
        assertTrue(detail.contains(fragment), "Problem detail 应包含 [" + fragment + "]，实际：" + detail);
        return this;
    }

    /** 断言字符串字段。 */
    public PayoutResponseChecker assertText(String field, String expected) {
        assertEquals(expected, text(field), field);
        return this;
    }

    /** 断言枚举 Code 字段。 */
    public PayoutResponseChecker assertCode(String field, CodeEnum expected) {
        return assertText(field, expected.getCode());
    }

    /** 读取字符串字段；缺失或 null 时为 null。 */
    public String text(String field) {
        JsonNode node = body().path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** 读取长整型 ID 字段。 */
    public long id(String field) {
        return Long.parseLong(text(field));
    }

    /** 完整 JSON。 */
    public JsonNode json() {
        return body();
    }

    /** 原始响应文本（零泄漏检查）。 */
    public String raw() {
        return raw;
    }
}
