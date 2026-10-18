package com.paytech.platform.support.test.checker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.foundation.testsupport.api.AbstractHttpResponseChecker;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;
import com.paytech.platform.support.test.builder.CredentialFixture;

import tools.jackson.databind.ObjectMapper;

/** 在 Foundation 协议断言上补充 PLT-I03 身份、Scope 和秘密不外泄断言。 */
public class ApiCredentialResponseChecker extends AbstractHttpResponseChecker<ApiCredentialResponseChecker> {

    private final ObjectMapper objectMapper;

    /** 保留单次响应和预期 Request ID，不提前假定成功。 */
    public ApiCredentialResponseChecker(EntityExchangeResult<byte[]> result, ObjectMapper mapper, String requestId) {
        super(result, mapper, requestId);
        this.objectMapper = mapper;
    }

    /** 保持父类通用断言后的链式业务检查。 */
    @Override
    protected ApiCredentialResponseChecker self() {
        return this;
    }

    /** 同时验证原始 Wire 数量、顺序及正式响应 DTO，避免 DTO 的去重排序掩盖问题。 */
    public ApiCredentialResponseChecker assertIdentity(CredentialFixture fixture, String... scopes) {
        assertStatus(200).assertContentType(MediaType.APPLICATION_JSON).assertRequestId();
        assertEquals(5, body().size(), "成功响应只能包含五项可信身份字段");
        for (String field : List.of("tenantId", "tenantType", "ownerRefId", "credentialId")) {
            assertTrue(body().path(field).isString(), "身份字段必须使用字符串：" + field);
        }
        assertEquals(List.of(scopes),
            body().path("effectiveScopeCodes").valueStream().map(value -> value.asString()).toList());
        var response = objectMapper.readValue(result.getResponseBody(), ValidatedCredentialResponseBody.class);
        assertAll(() -> assertEquals(Long.toString(fixture.getTenantId()), response.getTenantId()),
            () -> assertEquals("CLIENT", response.getTenantType()),
            () -> assertEquals(Long.toString(fixture.getOwnerRefId()), response.getOwnerRefId()),
            () -> assertEquals(Long.toString(fixture.getCredentialId()), response.getCredentialId()),
            () -> assertEquals(List.of(scopes), response.getEffectiveScopeCodes()));
        return this;
    }

    /** 错误、缺失和失效凭证统一拒绝，不提供部分身份。 */
    public ApiCredentialResponseChecker assertUnauthenticated() {
        assertProblem(401, "API_CREDENTIAL_AUTHENTICATION_FAILED");
        assertEquals(6, body().size(), "失败响应不得携带身份、Scope 或认证材料");
        return this;
    }

    /** 损坏事实必须失败关闭，不伪装成普通凭证错误或部分成功。 */
    public ApiCredentialResponseChecker assertUnavailable() {
        assertProblem(503, "API_CREDENTIAL_VALIDATION_UNAVAILABLE");
        assertEquals(6, body().size(), "不可用响应不得携带持久化事实或 Cause");
        return this;
    }
}
