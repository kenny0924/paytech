package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 通用 JSON 响应 Checker：状态、Problem 与响应体读取。 */
public final class JsonResponseChecker extends AbstractHttpResponseChecker<JsonResponseChecker> {

    /** 包装一次 HTTP 交换结果。 */
    public JsonResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
    }

    /** 自身。 */
    @Override
    protected JsonResponseChecker self() {
        return this;
    }

    /** 断言 200 并返回响应体。 */
    public JsonNode ok() {
        assertStatus(200).assertRequestId();
        return body();
    }

    /** 断言 200 命令结果的主案件状态。 */
    public JsonResponseChecker assertCommand(long caseId, String status) {
        JsonNode body = ok();
        assertEquals(Long.toString(caseId), body.path("caseId").asString(), "caseId");
        assertEquals(status, body.path("status").asString(), "status");
        return this;
    }

    /** 响应体。 */
    public JsonNode json() {
        return body();
    }
}
