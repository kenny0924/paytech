package com.paytech.client.master.test.base.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.base.ClientMasterMockTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 入驻 Client 侧真实 HTTP 入口：开始申请、草稿、人员、材料、声明、表单规格、需求清单、提交与撤回；不含 @Test。 */
public abstract class AbstractOnboardingHttpTest extends ClientMasterMockTestBase {
    /** 文件内容摘要。 */
    protected static final String HASH = "a".repeat(64);
    /** JSON。 */
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    /** C01 开始申请；null 参数不出现在请求体。新 Client 的工作区首版开通由 Platform 受理。 */
    protected EntityExchangeResult<byte[]> start(String clientType, String jurisdiction, String organizationType, String code) {
        owners.mockProvisionAccepted();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("clientType", clientType);
        put(body, "jurisdictionCode", jurisdiction);
        put(body, "organizationTypeCode", organizationType);
        put(body, "onboardingCode", code);
        return http.post().uri("/client-onboarding/v1/applications").headers(this::client).header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(body)).exchange().expectBody(byte[].class).returnResult();
    }

    /** C01 成功并返回申请 ID。 */
    protected long started(String clientType, String jurisdiction, String organizationType, String code) {
        var result = start(clientType, jurisdiction, organizationType, code);
        assertEquals(201, result.getStatus().value(), new String(result.getResponseBody()));
        long applicationId = Long.parseLong(body(result).path("applicationId").asString());
        applications.track(applicationId);
        return applicationId;
    }

    /** C04 保存草稿：values 为 分区 → 字段 → 取值，dirtyFields 自动生成。 */
    protected EntityExchangeResult<byte[]> saveDraft(long applicationId, Map<String, Map<String, String>> values) {
        return http.patch().uri("/client-onboarding/v1/applications/{id}/draft", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(Map.of("dirtyFields", dirty(values), "values", values)))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** C04 必须成功。 */
    protected void saved(long applicationId, Map<String, Map<String, String>> values) {
        var result = saveDraft(applicationId, values);
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
    }

    /** C05 读取申请。 */
    protected JsonNode draft(long applicationId) {
        var result = http.get().uri("/client-onboarding/v1/applications/{id}", applicationId).headers(this::client).exchange()
            .expectBody(byte[].class).returnResult();
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        return body(result);
    }

    /** 新建人员与关系，返回人员 Party ID。 */
    protected long addPerson(long applicationId, Map<String, Map<String, String>> person, Map<String, String> relation) {
        var body = Map.of("person", Map.of("dirtyFields", dirty(person), "values", person), "relation", relation);
        var result = http.post().uri("/client-onboarding/v1/applications/{id}/people", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(body)).exchange().expectBody(byte[].class)
            .returnResult();
        assertEquals(201, result.getStatus().value(), new String(result.getResponseBody()));
        return Long.parseLong(body(result).path("partyId").asString());
    }

    /** 关联一份可用材料（Platform 文件为当前账号上传且已扫描）。 */
    protected void attach(long applicationId, long subjectPartyId, String requirementCode, String documentType) {
        long fileId = applications.nextId();
        owners.mockFile(ACCOUNT_ID, fileId, HASH, "AVAILABLE");
        var document = Map.of("subjectPartyId", Long.toString(subjectPartyId), "requirementCode", requirementCode, "documentType",
            documentType, "fileAddress", "/files/v1/" + fileId);
        var result = http.post().uri("/client-onboarding/v1/applications/{id}/documents", applicationId).headers(this::client)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(document)).exchange().expectBody(byte[].class)
            .returnResult();
        assertEquals(201, result.getStatus().value(), new String(result.getResponseBody()));
    }

    /** CM-6 签署声明。 */
    protected EntityExchangeResult<byte[]> declare(long applicationId, String type) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/declarations", applicationId).headers(this::client)
            .header("Content-Type", "application/json")
            .body(JSON.writeValueAsString(Map.of("declarationType", type, "declaredValue", "TRUE", "termsVersion", "2026-10")))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** CM-2 表单规格；参数为 名称、取值 交替，null 取值跳过。 */
    protected EntityExchangeResult<byte[]> formSchema(String... params) {
        var query = new StringBuilder("/client-onboarding/v1/form-schema?");
        for (int i = 0; i < params.length; i += 2) {
            if (params[i + 1] != null) {
                query.append(params[i]).append('=').append(params[i + 1]).append('&');
            }
        }
        return http.get().uri(query.toString()).headers(this::client).exchange().expectBody(byte[].class).returnResult();
    }

    /** 辖区列表。 */
    protected EntityExchangeResult<byte[]> jurisdictions() {
        return http.get().uri("/client-onboarding/v1/jurisdictions").headers(this::client).exchange().expectBody(byte[].class)
            .returnResult();
    }

    /** 需求清单。 */
    protected EntityExchangeResult<byte[]> requirements(long applicationId) {
        return http.get().uri("/client-onboarding/v1/applications/{id}/requirements", applicationId).headers(this::client).exchange()
            .expectBody(byte[].class).returnResult();
    }

    /** 提交前核对的错误，每项为 需求键=错误码。 */
    protected List<String> checkErrors(long applicationId) {
        var result = http.get().uri("/client-onboarding/v1/applications/{id}/submission-check", applicationId).headers(this::client)
            .exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        var errors = new ArrayList<String>();
        body(result).path("errors").forEach(item -> errors.add(item.path("requirementKey").asString() + "=" + item.path("code").asString()));
        return errors;
    }

    /** C14 提交。 */
    protected EntityExchangeResult<byte[]> submit(long applicationId) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/submissions", applicationId).headers(this::client).exchange()
            .expectBody(byte[].class).returnResult();
    }

    /** 撤回申请。 */
    protected EntityExchangeResult<byte[]> withdraw(long applicationId) {
        return http.post().uri("/client-onboarding/v1/applications/{id}/withdrawal", applicationId).headers(this::client).exchange()
            .expectBody(byte[].class).returnResult();
    }

    /** 响应 JSON。 */
    protected static JsonNode body(EntityExchangeResult<byte[]> result) {
        return JSON.readTree(result.getResponseBody());
    }

    /** 两层 Map 简写。 */
    protected static Map<String, Map<String, String>> values(String group, String... fieldValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < fieldValues.length; i += 2) {
            fields.put(fieldValues[i], fieldValues[i + 1]);
        }
        return Map.of(group, fields);
    }

    /** 可信 Client BFF 调用头。 */
    protected void client(HttpHeaders headers) {
        headers.set("X-Caller-Service", "client-console-bff");
        headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
        headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
        headers.set("X-Request-Id", "cct-onboarding-http-test");
    }

    /** values 的全部叶路径。 */
    static List<String> dirty(Map<String, Map<String, String>> values) {
        var paths = new ArrayList<String>();
        values.forEach((group, fields) -> fields.keySet().forEach(field -> paths.add(group + "." + field)));
        return paths;
    }

    /** 非空才放入。 */
    static void put(Map<String, String> body, String key, String value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
