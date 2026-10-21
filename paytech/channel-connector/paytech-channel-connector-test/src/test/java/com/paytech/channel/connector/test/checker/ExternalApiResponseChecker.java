package com.paytech.channel.connector.test.checker;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 外部 API 管理入口的通用响应断言（2026-10-20 随管理接口自 Platform 迁入）：成功响应读取为正式 DTO，错误复用公共 Problem 断言。 */
public class ExternalApiResponseChecker extends AbstractHttpResponseChecker<ExternalApiResponseChecker> {

    private final ObjectMapper mapper;

    /**
     * 保留单次响应和预期 Request ID，不提前假定成功
     *
     * @param result 真实 HTTP 交换结果
     * @param mapper JSON 读取器
     * @param requestId 请求携带的 Request ID
     */
    public ExternalApiResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 以错误码枚举检查 Problem，避免在用例里手写错误码文字。 */
    public ExternalApiResponseChecker assertProblem(int expectedStatus, CodeEnum expectedCode) {
        return assertProblem(expectedStatus, expectedCode.getCode());
    }

    /** 保持链式断言返回当前 Checker。 */
    @Override
    protected ExternalApiResponseChecker self() {
        return this;
    }

    /** 200 JSON 成功响应，并携带同一 Request ID。 */
    public ExternalApiResponseChecker assertOk() {
        return assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    /** 以正式响应 DTO 读取 Body。 */
    public <T> T as(Class<T> type) {
        return mapper.readValue(result.getResponseBody(), type);
    }

    /** 以泛型正式 DTO 读取 Body，例如分页响应。 */
    public <T> T as(TypeReference<T> type) {
        return mapper.readValue(result.getResponseBody(), type);
    }

    /** 原始 JSON，用于检查字段集合、类型和显式 null。 */
    public JsonNode json() {
        return body();
    }

    /** 原始响应文本，用于秘密不外泄检查。 */
    public String text() {
        return new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
            StandardCharsets.UTF_8);
    }
}
