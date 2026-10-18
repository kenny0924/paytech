package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * C04 保存资料的错误响应必须符合 Foundation Problem 合同，否则 Client BFF 的严格解码器会把业务校验错误当成"依赖响应异常"（502）。
 * <li>标题按 Spring 对该状态码的标准短语（422 为 Unprocessable Content）；此前 client-master 用了已废弃的 UNPROCESSABLE_ENTITY，标题变成
 * Unprocessable Entity，浏览器收不到字段错误提示。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save
 */
class SaveDraftProblemContractTest extends AbstractSubmissionApiTest {
    private static final String REQUEST_ID = "cct-draft-problem-test";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 不带国家码的手机号被判为 INVALID_DRAFT：422、标题与状态码一致、Problem 各成员通过 Foundation 校验。 */
    @Test
    void test_invalidDraftProblemMatchesFoundationProfile() {
        var app = applications.individual(ACCOUNT_ID);

        var result = saveDraft(app, Map.of("contact", Map.of("phone", "13800138000")));

        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        assertEquals(ApiProblemProfile.CONTENT_TYPE, result.getResponseHeaders().getContentType().toString());
        ApiProblemResponse problem = JSON.readValue(result.getResponseBody(), ApiProblemResponse.class);
        assertEquals(ClientDraftErrorCode.INVALID_DRAFT.getCode(), problem.code());
        assertDoesNotThrow(() -> ApiProblemProfile.validate(problem,
            HttpStatus.resolve(result.getStatus().value()).getReasonPhrase(),
            result.getResponseHeaders().getFirst(ApiProblemProfile.REQUEST_ID_HEADER)));
    }

    /** 通过真实 HTTP 调用 C04；dirtyFields 由 values 推导。 */
    EntityExchangeResult<byte[]> saveDraft(ApplicationFixture app, Map<String, Map<String, String>> values) {
        List<String> dirty = values.entrySet().stream()
            .flatMap(group -> group.getValue().keySet().stream().map(field -> group.getKey() + "." + field))
            .toList();
        return http.patch().uri("/client-onboarding/v1/applications/{id}/draft", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", REQUEST_ID)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("dirtyFields", dirty, "values", values)))
            .exchange().expectBody(byte[].class).returnResult();
    }
}
