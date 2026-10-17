package com.paytech.platform.support.test.checker;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Platform 入口的通用响应断言：成功响应读取为正式 DTO，错误复用公共 Problem 断言。 */
public class PlatformResponseChecker extends AbstractHttpResponseChecker<PlatformResponseChecker> {

    private final ObjectMapper mapper;

    /** 保留单次响应和预期 Request ID，不提前假定成功。 */
    public PlatformResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.mapper = mapper;
    }

    /** 以错误码枚举检查 Problem，避免在用例里手写错误码文字。 */
    public PlatformResponseChecker assertProblem(int expectedStatus, CodeEnum expectedCode) {
        return assertProblem(expectedStatus, expectedCode.getCode());
    }

    @Override
    protected PlatformResponseChecker self() {
        return this;
    }

    /** 200 JSON 成功响应，并携带同一 Request ID。 */
    public PlatformResponseChecker assertOk() {
        return assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
    }

    /** 无返回值的 200 成功响应。 */
    public PlatformResponseChecker assertNoContent() {
        return assertNoContent(200);
    }

    /** 无返回值的成功响应，状态由接口合同决定（200 或 204）。 */
    public PlatformResponseChecker assertNoContent(int status) {
        return assertStatus(status).assertRequestId().assertEmptyBody();
    }

    /** 以正式响应 DTO 读取 Body。 */
    public <T> T as(Class<T> type) {
        return mapper.readValue(result.getResponseBody(), type);
    }

    /** 以泛型正式 DTO 读取 Body，例如分页响应。 */
    public <T> T as(tools.jackson.core.type.TypeReference<T> type) {
        return mapper.readValue(result.getResponseBody(), type);
    }

    /** 原始 JSON，用于检查字段集合、类型和显式 null。 */
    public JsonNode json() {
        return body();
    }

    /** 原始响应文本，用于秘密不外泄检查。 */
    public String text() {
        return new String(result.getResponseBody() == null ? new byte[0] : result.getResponseBody(),
            java.nio.charset.StandardCharsets.UTF_8);
    }
}
