package com.paytech.commercial.test.checker.write;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 运营写入与读取接口的响应检查：公共 Problem 断言来自基类，这里只读取并断言业务字段。 */
public class CommercialWriteResponseChecker extends AbstractHttpResponseChecker<CommercialWriteResponseChecker> {
    /** 保存实际 HTTP 结果与本例 Request ID。 */
    public CommercialWriteResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    /** 返回当前检查器。 */
    @Override
    protected CommercialWriteResponseChecker self() {
        return this;
    }

    /** 200 且为 JSON。 */
    public CommercialWriteResponseChecker assertOk() {
        assertEquals(200, status(), () -> "期望 200，实际 " + status() + " " + new String(result.getResponseBody()));
        return this;
    }

    /** 400 BUSINESS_CHECK_FAILED，detail 含给定片段。 */
    public CommercialWriteResponseChecker assertBusinessCheckFailed(String detailFragment) {
        assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertTrue(body().path("detail").asString().contains(detailFragment),
            () -> "detail 不含 " + detailFragment + "：" + body().path("detail").asString());
        return this;
    }

    /** 409 并发 / 版本冲突。 */
    public CommercialWriteResponseChecker assertConflict() {
        return assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
    }

    /** 400 INVALID_REQUEST。 */
    public CommercialWriteResponseChecker assertInvalidRequest() {
        return assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
    }

    /** 422 VALIDATION_ERROR，并带该字段的字段错误（Body 注解校验）。 */
    public CommercialWriteResponseChecker assertValidationError(String field) {
        return assertProblem(422, CommonErrorCode.VALIDATION_ERROR.getCode()).assertFieldError(field);
    }

    /** 文本字段等于期望值。 */
    public CommercialWriteResponseChecker assertText(String field, String expected) {
        assertEquals(expected, body().path(field).asString(null), "字段不符：" + field);
        return this;
    }

    /** changeVersion 等于期望值。 */
    public CommercialWriteResponseChecker assertVersion(long expected) {
        assertEquals(expected, body().path("changeVersion").asLong(), "changeVersion 不符");
        return this;
    }

    /** 读取文本字段。 */
    public String text(String field) {
        return body().path(field).asString(null);
    }

    /** 读取 ID 字段为 long。 */
    public long id(String field) {
        return Long.parseLong(body().path(field).asString());
    }

    /** 读取嵌套节点。 */
    public JsonNode node(String field) {
        return body().path(field);
    }

    /** 读取文本数组。 */
    public List<String> texts(String field) {
        return body().path(field).valueStream().map(JsonNode::asString).toList();
    }
}
