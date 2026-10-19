package com.paytech.account.test.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Account HTTP 响应断言：复用公共状态、Request ID 与 Problem 检查，补充 JSON 字段读取和业务拒绝断言。 */
public final class AccountResponseChecker extends AbstractHttpResponseChecker<AccountResponseChecker> {
    private final ObjectMapper mapper;

    public AccountResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected AccountResponseChecker self() {
        return this;
    }

    /** 断言成功状态、JSON 媒体类型与 Request ID。 */
    public AccountResponseChecker assertSuccess(int status) {
        return assertStatus(status).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    /** 断言 400 业务拒绝，且提示包含给定业务上下文。 */
    public AccountResponseChecker assertBusinessRejected(String detailFragment) {
        assertProblem(400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode());
        assertDetailContains(detailFragment);
        return this;
    }

    /** 断言依赖不可用时失败关闭为 503。 */
    public AccountResponseChecker assertServiceUnavailable() {
        assertProblem(503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        return this;
    }

    /** 断言 400 专用错误码拒绝，detail 为该错误码的默认消息。 */
    public AccountResponseChecker assertRejected(CodeMessageEnum errorCode) {
        assertProblem(400, errorCode.getCode());
        assertEquals(errorCode.getMessage(), body().path("detail").asString(), "detail");
        return this;
    }

    /** 断言 400 参数错误。 */
    public AccountResponseChecker assertInvalidRequest() {
        assertProblem(400, CommonErrorCode.INVALID_REQUEST.getCode());
        return this;
    }

    /** 断言 409 并发冲突。 */
    public AccountResponseChecker assertConcurrentRequest() {
        assertProblem(409, CommonErrorCode.CONCURRENT_REQUEST.getCode());
        return this;
    }

    /** 断言 Problem detail 包含给定片段。 */
    public AccountResponseChecker assertDetailContains(String fragment) {
        String detail = body().path("detail").asString();
        assertTrue(detail.contains(fragment), "Problem detail 应包含 [" + fragment + "]，实际：" + detail);
        return this;
    }

    /** 断言字符串字段值。 */
    public AccountResponseChecker assertText(String field, String expected) {
        assertEquals(expected, text(field), field);
        return this;
    }

    /** 读取字符串字段；缺失或为 null 时返回 null。 */
    public String text(String field) {
        var node = body().path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    /** 读取长整型 ID 字段。 */
    public long id(String field) {
        return Long.parseLong(text(field));
    }

    /** 断言分页结果的页码、条数与总数，返回当前页 items。 */
    public List<JsonNode> assertPage(int page, int size, long total) {
        assertEquals(page, body().path("page").asInt(), "page");
        assertEquals(size, body().path("size").asInt(), "size");
        assertEquals(total, body().path("total").asLong(), "total");
        List<JsonNode> items = new ArrayList<>();
        body().path("items").forEach(items::add);
        return items;
    }

    /** 读取完整 JSON 响应体，用于嵌套集合断言。 */
    public JsonNode json() {
        return body();
    }

    /** 按响应类型反序列化成功响应。 */
    public <T> T as(Class<T> type) {
        return mapper.readValue(result.getResponseBody(), type);
    }
}
