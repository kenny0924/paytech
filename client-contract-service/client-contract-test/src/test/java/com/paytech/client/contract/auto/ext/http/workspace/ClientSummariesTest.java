package com.paytech.client.contract.auto.ext.http.workspace;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.domain.enums.ClientStatus;
import com.paytech.client.contract.domain.enums.ClientType;
import com.paytech.client.contract.test.base.onboarding.OnboardingMockTestBase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 工作区摘要：返回已批准的 Client（不判断申请人或成员关系），其余静默省略；参数与调用方校验。
 *
 * @see com.paytech.client.contract.api.workspace.ClientWorkspaceApi#summaries(String, String, String, List)
 */
class ClientSummariesTest extends OnboardingMockTestBase {
    private static final String MICROS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z";

    private final ObjectMapper json = new ObjectMapper();

    /** 组织与个人各一份已批准申请：按 Client ID 升序返回展示摘要，不含证件、地址、联系方式。 */
    @Test
    void test_normalFlow() {
        var organization = applications.approved(ACCOUNT_ID, true);
        var individual = applications.approved(ACCOUNT_ID, false);

        var result = summaries("client-console-bff", ACCOUNT_ID, organization.clientId(), individual.clientId());

        assertEquals(200, result.getStatus().value(), body(result).toString());
        var items = body(result).path("items");
        assertEquals(2, items.size());
        var org = item(items, organization.clientId());
        assertEquals("TC" + organization.clientId(), org.path("clientNo").asString());
        assertEquals(ClientType.ORGANIZATION.getCode(), org.path("clientType").asString());
        assertEquals(ClientStatus.ACTIVE.getCode(), org.path("clientStatus").asString());
        assertEquals("Fixture Trading LLC", org.path("displayName").asString());
        assertEquals("AE", org.path("country").asString());
        assertEquals("SERVICES", org.path("businessModelCode").asString());
        assertTrue(org.path("approvedAt").asString().matches(MICROS), org.toString());
        var person = item(items, individual.clientId());
        assertEquals(ClientType.INDIVIDUAL.getCode(), person.path("clientType").asString());
        assertEquals("Alex Fixture", person.path("displayName").asString());
        assertEquals("AE", person.path("country").asString());
        String raw = body(result).toString();
        assertFalse(raw.contains("Fixture Road") || raw.contains("vt_email") || raw.contains("p 12-"), raw);
        assertTrue(items.get(0).path("clientId").asLong() < items.get(1).path("clientId").asLong());
    }

    /** 未批准、不存在的 ID 静默省略，不区分原因。 */
    @Test
    void test_noDataFound() {
        var own = applications.approved(ACCOUNT_ID, true);
        var draft = applications.organization(ACCOUNT_ID + 1);

        var result = summaries("client-console-bff", ACCOUNT_ID, own.clientId(), draft.clientId(), applications.nextId());

        assertEquals(200, result.getStatus().value());
        var items = body(result).path("items");
        assertEquals(1, items.size());
        assertEquals(Long.toString(own.clientId()), items.get(0).path("clientId").asString());
        assertEquals(0, body(summaries("client-console-bff", ACCOUNT_ID, draft.clientId())).path("items").size());
    }

    /** 被邀请成员（非申请人）读取 BFF 按 Platform 成员列表传入的已批准 Client，同样返回摘要。 */
    @Test
    void test_nonApplicantMember() {
        var invited = applications.approved(ACCOUNT_ID + 2, false);

        var result = summaries("client-console-bff", ACCOUNT_ID, invited.clientId());

        assertEquals(200, result.getStatus().value(), body(result).toString());
        var items = body(result).path("items");
        assertEquals(1, items.size());
        assertEquals(ClientType.INDIVIDUAL.getCode(), item(items, invited.clientId()).path("clientType").asString());
    }

    /** 租户上下文会话同样可读。 */
    @Test
    void test_tenantContext() {
        var own = applications.approved(ACCOUNT_ID, false);
        owners.mockTenantSession(ACCOUNT_ID, SESSION_ID, applications.nextId(), applications.nextId());

        var result = summaries("client-console-bff", ACCOUNT_ID, own.clientId());

        assertEquals(200, result.getStatus().value());
        assertEquals(1, body(result).path("items").size());
    }

    /** 缺失、超过 50 个、重复、非规范 ID 返回 400，不调用 Platform。 */
    @Test
    void test_invalidParameter() {
        assertEquals(400, get("/client-workspace/v1/client-summaries", "client-console-bff", ACCOUNT_ID).getStatus().value());
        String many = LongStream.rangeClosed(1, 51).mapToObj(id -> "clientId=" + id).collect(Collectors.joining("&"));
        assertEquals(400, get("/client-workspace/v1/client-summaries?" + many, "client-console-bff", ACCOUNT_ID).getStatus().value());
        assertEquals(400,
            get("/client-workspace/v1/client-summaries?clientId=7&clientId=7", "client-console-bff", ACCOUNT_ID).getStatus().value());
        assertEquals(400, get("/client-workspace/v1/client-summaries?clientId=07", "client-console-bff", ACCOUNT_ID).getStatus().value());
        assertEquals(0, owners.count("context"));
    }

    /** 非 Client BFF 调用按不存在隐藏，不调用 Platform；非规范账号坐标按会话失效返回 401。 */
    @Test
    void test_businessRuleViolation() {
        var own = applications.approved(ACCOUNT_ID, true);

        assertEquals(404, summaries("unknown", ACCOUNT_ID, own.clientId()).getStatus().value());
        var invalid = http.get().uri("/client-workspace/v1/client-summaries?clientId=" + own.clientId())
            .header("X-Caller-Service", "client-console-bff").header("X-Gateway-Actor-Subject", "0")
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).exchange().expectBody(byte[].class).returnResult();
        assertEquals(401, invalid.getStatus().value());
        assertEquals("CLIENT_SESSION_INVALID", body(invalid).path("code").asString());
        assertEquals(0, owners.count("context"));
    }

    /** 通过真实 HTTP 调用摘要接口。 */
    EntityExchangeResult<byte[]> summaries(String caller, long accountId, long... clientIds) {
        String query = LongStream.of(clientIds).mapToObj(id -> "clientId=" + id).collect(Collectors.joining("&"));
        return get("/client-workspace/v1/client-summaries?" + query, caller, accountId);
    }

    /** 带可信调用坐标的 GET。 */
    EntityExchangeResult<byte[]> get(String uri, String caller, long accountId) {
        return http.get().uri(uri).header("X-Caller-Service", caller).header("X-Gateway-Actor-Subject", Long.toString(accountId))
            .header("X-Gateway-Session-Id", Long.toString(SESSION_ID)).header("X-Request-Id", "cct-workspace-test")
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 响应 JSON。 */
    JsonNode body(EntityExchangeResult<byte[]> result) {
        return json.readTree(result.getResponseBody());
    }

    /** 按 Client ID 取一项。 */
    static JsonNode item(JsonNode items, long clientId) {
        for (JsonNode node : items) {
            if (Long.toString(clientId).equals(node.path("clientId").asString())) {
                return node;
            }
        }
        throw new AssertionError("缺少 Client " + clientId);
    }
}
