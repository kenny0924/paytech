package com.paytech.client.master.auto.ext.http.onboarding;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.api.enums.ClientDraftErrorCode;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.DraftPatchRequestBody;
import com.paytech.client.contract.api.onboarding.ClientDraftModels.StartApplicationRequestBody;
import com.paytech.client.master.test.base.onboarding.AbstractSubmissionApiTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ApplicationFixture;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;

import tools.jackson.databind.json.JsonMapper;

/**
 * C04 保存资料的错误响应必须符合 Foundation Problem 合同，否则 Client BFF 的严格解码器会把业务校验错误当成"依赖响应异常"（502）。
 * <li>标题按 Spring 对该状态码的标准短语（422 为 Unprocessable Content）；此前 client-master 用了已废弃的 UNPROCESSABLE_ENTITY，标题变成
 * Unprocessable Entity，浏览器收不到字段错误提示。</li>
 *
 * <li>INVALID_DRAFT 一次返回全部字段问题 errors[]（路径与 dirtyFields 同名），消息说明约束且不回显提交值；校验失败不调用 Vault、不落库。</li>
 *
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#save(String, String, String, String, DraftPatchRequestBody)
 * @see com.paytech.client.contract.api.onboarding.ClientDraftApi#create(String, String, String, StartApplicationRequestBody)
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
        assertEquals(List.of(new ProblemFieldError("contact.phone", "手机号须为 E.164 格式，如 +8613800138000")),
            problem.errors());
    }

    /** 多个字段同时不合规：全部按路径排序返回，合规字段不报错，响应不回显提交值。 */
    @Test
    void test_invalidDraftReportsEveryFieldAtOnce() {
        var app = applications.individual(ACCOUNT_ID);

        var result = saveDraft(app, Map.of(
            "contact", Map.of("phone", "13912345678", "email", "not-an-email"),
            "individual", Map.of("birthDate", "1990/05/01", "givenName", "Alex"),
            "address", Map.of("countryCode", "ARE")));

        ApiProblemResponse problem = problem(result);
        assertEquals(List.of(
            new ProblemFieldError("address.countryCode", "须为两位国家代码，如 CN"),
            new ProblemFieldError("contact.email", "邮箱格式不正确"),
            new ProblemFieldError("contact.phone", "手机号须为 E.164 格式，如 +8613800138000"),
            new ProblemFieldError("individual.birthDate", "须为 YYYY-MM-DD 格式的有效日期")), problem.errors());
        String body = new String(result.getResponseBody());
        assertFalse(body.contains("13912345678") || body.contains("not-an-email") || body.contains("1990/05/01"), body);
    }

    /** dirtyFields 与 values 不一致：未列出的取值按字段路径报告，列出但缺值的字段也逐项报告。 */
    @Test
    void test_dirtyFieldsMismatchIsReportedPerPath() {
        var app = applications.individual(ACCOUNT_ID);

        var result = save(app, List.of("contact.phone", "address.city"),
            Map.of("contact", Map.of("phone", "+8613800138000", "email", "fixture@example.test")));

        assertEquals(List.of(
            new ProblemFieldError("address.city", "已列入 dirtyFields，但 values 中没有该字段"),
            new ProblemFieldError("contact.email", "该字段未列入 dirtyFields")), problem(result).errors());
    }

    /** 个人申请写组织字段：按字段路径说明当前类型不支持。 */
    @Test
    void test_fieldOfOtherClientTypeIsReportedPerPath() {
        var app = applications.individual(ACCOUNT_ID);

        var result = saveDraft(app, Map.of("organization", Map.of("legalName", "Fixture LLC")));

        assertEquals(List.of(new ProblemFieldError("organization.legalName", "当前申请类型不支持此字段")),
            problem(result).errors());
    }

    /** 开始申请的类型不在目录内：INVALID_DRAFT 指向 clientType。 */
    @Test
    void test_unknownClientTypeIsReportedOnClientType() {
        var result = http.post().uri("/client-onboarding/v1/applications")
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", REQUEST_ID)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("clientType", "PARTNERSHIP")))
            .exchange().expectBody(byte[].class).returnResult();

        assertEquals(List.of(new ProblemFieldError("clientType", "须为 INDIVIDUAL 或 ORGANIZATION")),
            problem(result).errors());
    }

    /** 断言 422 INVALID_DRAFT 且通过 Foundation Problem 校验后返回 Problem。 */
    ApiProblemResponse problem(EntityExchangeResult<byte[]> result) {
        assertEquals(422, result.getStatus().value(), new String(result.getResponseBody()));
        ApiProblemResponse problem = JSON.readValue(result.getResponseBody(), ApiProblemResponse.class);
        assertEquals(ClientDraftErrorCode.INVALID_DRAFT.getCode(), problem.code());
        assertDoesNotThrow(() -> ApiProblemProfile.validate(problem, HttpStatus.UNPROCESSABLE_CONTENT.getReasonPhrase(),
            result.getResponseHeaders().getFirst(ApiProblemProfile.REQUEST_ID_HEADER)));
        return problem;
    }

    /** 通过真实 HTTP 调用 C04；dirtyFields 由 values 推导。 */
    EntityExchangeResult<byte[]> saveDraft(ApplicationFixture app, Map<String, Map<String, String>> values) {
        List<String> dirty = values.entrySet().stream()
            .flatMap(group -> group.getValue().keySet().stream().map(field -> group.getKey() + "." + field))
            .toList();
        return save(app, dirty, values);
    }

    /** 通过真实 HTTP 调用 C04，dirtyFields 与 values 由用例分别给出。 */
    EntityExchangeResult<byte[]> save(ApplicationFixture app, List<String> dirty, Map<String, Map<String, String>> values) {
        return http.patch().uri("/client-onboarding/v1/applications/{id}/draft", app.applicationId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", REQUEST_ID)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("dirtyFields", dirty, "values", values)))
            .exchange().expectBody(byte[].class).returnResult();
    }
}
