package com.paytech.foundation.testsupport.api;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.http.ApiHeaderNames;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP 响应检查基类；只读取结果，业务断言由各服务子类提供。
 *
 * @param <S> 具体 Checker 类型，公共断言后可继续调用业务断言
 */
public abstract class AbstractHttpResponseChecker<S extends AbstractHttpResponseChecker<S>> {
    protected final EntityExchangeResult<byte[]> result;
    private final ObjectMapper mapper;
    private final String requestId;
    private JsonNode jsonBody;

    /** 保留实际响应及预期 Request ID；仅在 JSON 断言需要时解析 Body。 */
    protected AbstractHttpResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        this.result = result;
        this.mapper = mapper;
        this.requestId = requestId;
    }

    /** 子类返回自身，保持链式断言的具体类型。 */
    protected abstract S self();

    /** 检查实际 HTTP 状态，不把所有接口固定为 200。 */
    public final S assertStatus(int expected) {
        assertEquals(expected, status(), "HTTP 状态不符");
        return self();
    }

    /** 检查兼容的响应媒体类型，允许合法 Charset 参数。 */
    public final S assertContentType(MediaType expected) {
        var actual = result.getResponseHeaders().getContentType();
        assertNotNull(actual, "响应缺少 Content-Type");
        assertTrue(actual.isCompatibleWith(expected), "响应 Content-Type 不符");
        return self();
    }

    /** 检查一个响应 Header 的首个值，期望 null 表示该 Header 不存在。 */
    public final S assertHeader(String name, String expected) {
        assertEquals(expected, result.getResponseHeaders().getFirst(name), "响应 Header 不符：" + name);
        return self();
    }

    /** Request ID 必须为一个 Header 值，并与调用方预期一致。 */
    public final S assertRequestId() {
        assertEquals(List.of(requestId), result.getResponseHeaders().get(ApiHeaderNames.REQUEST_ID),
            "响应 Request ID 不符");
        return self();
    }

    /** 检查 HEAD、204 等无内容响应，无需 JSON Body。 */
    public final S assertEmptyBody() {
        var content = result.getResponseBody();
        assertTrue(content == null || content.length == 0, "响应应没有 Body");
        return self();
    }

    /** 检查本项目统一 Problem 协议；业务错误码及预期 HTTP 状态由用例提供。 */
    public final S assertProblem(int expectedStatus, String expectedCode) {
        assertStatus(expectedStatus).assertContentType(MediaType.APPLICATION_PROBLEM_JSON).assertRequestId();
        var body = body();
        for (String field : List.of("type", "title", "detail", "code", "requestId", "timestamp")) {
            assertTrue(body.path(field).isString(), "Problem 字段必须为字符串：" + field);
        }
        assertAll(() -> assertEquals("about:blank", body.path("type").asString()),
            () -> assertEquals(HttpStatus.valueOf(expectedStatus).getReasonPhrase(), body.path("title").asString()),
            () -> assertEquals(expectedCode, body.path("code").asString()),
            () -> assertFalse(body.path("detail").asString("").isBlank(), "Problem 缺少 detail"),
            () -> assertEquals(requestId, body.path("requestId").asString()),
            () -> assertTrue(body.path("timestamp").asString("")
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z"), "Problem timestamp 必须为 UTC 六位小数"),
            () -> assertDoesNotThrow(() -> Instant.parse(body.path("timestamp").asString())),
            () -> assertFalse(body.has("status"), "Problem 不重复输出 HTTP status"),
            () -> assertFalse(body.has("instance"), "当前 Problem 协议不输出 instance"));
        return self();
    }

    /** 字段错误必须实际包含目标路径及非空消息，未命中明确失败。 */
    public final S assertFieldError(String field) {
        var errors = body().path("errors");
        assertTrue(errors.isArray(), "响应缺少字段错误数组");
        assertTrue(errors.valueStream().anyMatch(error -> field.equals(error.path("field").asString())
            && !error.path("message").asString("").isBlank()), "缺少字段错误：" + field);
        return self();
    }

    /** 返回实际状态，供并发用例区分成功和冲突结果。 */
    public final int status() {
        return result.getStatus().value();
    }

    /** 按需解析一次 JSON，解析失败直接暴露，不吞掉协议错误。 */
    protected final JsonNode body() {
        if (jsonBody == null) {
            jsonBody = mapper.readTree(result.getResponseBody());
        }
        return jsonBody;
    }
}
