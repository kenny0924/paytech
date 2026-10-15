package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.VaultAdminSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 浏览器到真实 BFF 与真实 VaultAdminClient：可信操作人转发、路由决定数据级别、提交 ID 幂等键、分页校验、
 * Vault 错误码状态映射及敏感值不进日志。
 *
 * <p>Vault 客户端的测试直连地址只在 unittest Profile 下允许，因此本类在 auto 之外叠加该 Profile。
 *
 * @see com.paytech.operations.console.bff.ext.rest.VaultAdminController
 */
@ActiveProfiles("unittest")
@ExtendWith(OutputCaptureExtension.class)
class VaultAdminBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String REQUEST_ID = "vault-admin-request-1";
    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
    private static final String SECRET_VALUE = "synthetic-channel-secret-canary";
    private static final VaultAdminSimulator VAULT = start();

    @Autowired
    private PlatformOperationsSimulator platform;
    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void vaultAddress(DynamicPropertyRegistry registry) {
        registry.add("vault.client.base-url", VAULT::baseUrl);
    }

    /** 每例重置 Platform 与 Vault 模拟端点。 */
    @BeforeEach
    void setUpClassMocks() {
        platform.reset();
        VAULT.reset();
    }

    /** 未配置的 Owner 调用使用例失败。 */
    @AfterEach
    void verifyNoUnexpectedCalls() {
        assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
            () -> assertTrue(VAULT.unexpectedRequests().isEmpty(), VAULT.unexpectedRequests().toString()));
    }

    @Test
    void test_listDataTypesForwardsOperatorAndPassesResponseThrough() throws Exception {
        String vaultBody = "{\"currentPolicyVersion\":3,\"items\":[{\"dataTypeCode\":\"EMAIL_ADDRESS_V1\",\"tokenMode\":\"STABLE\","
            + "\"dataClass\":\"STANDARD\",\"description\":\"邮箱\",\"manualProtectEnabled\":false,\"manualRevealEnabled\":true,"
            + "\"status\":\"ACTIVE\",\"version\":2,\"updateTime\":\"2026-10-15T01:02:03.000004Z\"}]}";
        VAULT.reply("GET", "/vault/v1/admin/data-types", vaultBody);

        var response = send("GET", "/vault-admin/v1/data-types?status=ACTIVE", "operations-vault-list-data-types", null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(JSON.readTree(vaultBody), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        var call = VAULT.requests().getFirst();
        assertEquals("status=ACTIVE", call.query());
        assertEquals(ACCOUNT_ID, call.header("X-Operator-Id"));
        assertEquals("operations-console-bff", call.header("X-Caller-Service"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Idempotency-Key"));
    }

    @Test
    void test_createDataTypeForwardsValidatedBody() throws Exception {
        VAULT.reply("POST", "/vault/v1/admin/data-types", "{\"dataType\":{\"dataTypeCode\":\"CHANNEL_ACCOUNT_CONFIG_V1\","
            + "\"tokenMode\":\"REFERENCE\",\"dataClass\":\"SECRET\",\"description\":\"渠道账号\",\"manualProtectEnabled\":true,"
            + "\"manualRevealEnabled\":true,\"status\":\"ACTIVE\",\"version\":1,\"updateTime\":\"2026-10-15T01:02:03Z\"},\"policyVersion\":4}");
        String body = "{\"dataTypeCode\":\"CHANNEL_ACCOUNT_CONFIG_V1\",\"tokenMode\":\"REFERENCE\",\"dataClass\":\"SECRET\","
            + "\"description\":\"渠道账号\",\"manualProtectEnabled\":true,\"manualRevealEnabled\":true,\"reason\":\"接入渠道\"}";

        var response = send("POST", "/vault-admin/v1/data-types", "operations-vault-create-data-type", body);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(4, JSON.readTree(response.body()).path("policyVersion").asInt());
        assertEquals(JSON.readTree(body), JSON.readTree(VAULT.requests().getFirst().body()));
    }

    @Test
    void test_accessRulePagingFollowsCommonRules() throws Exception {
        VAULT.reply("GET", "/vault/v1/admin/access-rules", "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}");

        var defaults = send("GET", "/vault-admin/v1/access-rules?dataTypeCode=EMAIL_ADDRESS_V1",
            "operations-vault-list-access-rules", null);
        var invalid = send("GET", "/vault-admin/v1/access-rules?size=30", "operations-vault-list-access-rules", null);

        assertEquals(200, defaults.statusCode(), defaults.body());
        assertEquals("dataTypeCode=EMAIL_ADDRESS_V1&page=1&size=10", VAULT.requests().getFirst().query());
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertEquals(1, VAULT.requests().size(), "非法分页不得调用 Vault");
    }

    @Test
    void test_manualProtectRouteDecidesDataClassesAndUsesSubmissionId(CapturedOutput output) throws Exception {
        VAULT.reply("POST", "/vault/v1/admin/manual/protect", "{\"token\":\"" + TOKEN + "\"}");
        String body = "{\"submissionId\":\"submission-001\",\"dataTypeCode\":\"CHANNEL_ACCOUNT_CONFIG_V1\",\"value\":\""
            + SECRET_VALUE + "\",\"reasonCode\":\"CHANNEL_CONFIGURATION\",\"ticketRef\":\"OPS-1024\"}";

        var standard = send("POST", "/vault-admin/v1/manual/protect", "operations-vault-manual-protect", body);
        var secret = send("POST", "/vault-admin/v1/manual/secret-protect", "operations-vault-manual-secret-protect", body);

        assertEquals(200, standard.statusCode(), standard.body());
        assertEquals(TOKEN, JSON.readTree(secret.body()).path("token").asString());
        List<VaultAdminSimulator.Request> calls = VAULT.requests();
        assertEquals(List.of("STANDARD"), classes(calls.get(0)));
        assertEquals(List.of("STANDARD", "SECRET"), classes(calls.get(1)));
        assertEquals("submission-001", calls.get(0).header("Idempotency-Key"), "提交 ID 作为 Vault 幂等键");
        assertEquals(SECRET_VALUE, JSON.readTree(calls.get(0).body()).path("value").asString());
        assertFalse(JSON.readTree(calls.get(0).body()).has("submissionId"));
        assertFalse(output.getAll().contains(SECRET_VALUE), "原值不得进入日志");
        assertFalse(output.getAll().contains(TOKEN), "Token 不得进入日志");
    }

    @Test
    void test_manualRevealReturnsValueWithoutLogging(CapturedOutput output) throws Exception {
        VAULT.reply("POST", "/vault/v1/admin/manual/reveal",
            "{\"dataTypeCode\":\"CHANNEL_ACCOUNT_CONFIG_V1\",\"value\":\"" + SECRET_VALUE + "\"}");
        String body = "{\"token\":\"" + TOKEN + "\",\"reasonCode\":\"CHANNEL_CONFIGURATION\",\"ticketRef\":\"OPS-1024\"}";

        var response = send("POST", "/vault-admin/v1/manual/secret-reveal", "operations-vault-manual-secret-reveal", body);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(SECRET_VALUE, JSON.readTree(response.body()).path("value").asString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(List.of("STANDARD", "SECRET"), classes(VAULT.requests().getFirst()));
        assertFalse(output.getAll().contains(SECRET_VALUE));
        assertFalse(output.getAll().contains(TOKEN));
    }

    @Test
    void test_invalidManualRequestAndDeniedRouteDoNotCallVault() throws Exception {
        String missingTicket = "{\"token\":\"" + TOKEN + "\",\"reasonCode\":\"CHANNEL_CONFIGURATION\"}";
        var invalid = send("POST", "/vault-admin/v1/manual/reveal", "operations-vault-manual-reveal", missingTicket);
        platform.allow(false);
        String body = "{\"token\":\"" + TOKEN + "\",\"reasonCode\":\"CHANNEL_CONFIGURATION\",\"ticketRef\":\"OPS-1\"}";
        var denied = send("POST", "/vault-admin/v1/manual/secret-reveal", "operations-vault-manual-secret-reveal", body);

        assertProblem(invalid, 422, "VALIDATION_ERROR");
        assertEquals("ticketRef", JSON.readTree(invalid.body()).path("errors").get(0).path("field").asString());
        assertEquals(403, denied.statusCode(), denied.body());
        assertTrue(VAULT.requests().isEmpty(), "校验失败或无权限不得调用 Vault");
    }

    @Test
    void test_vaultErrorCodesMapToBrowserStatuses() throws Exception {
        VAULT.problem("PUT", "/vault/v1/admin/data-types/EMAIL_ADDRESS_V1", 409, "POLICY_VERSION_CONFLICT");
        VAULT.problem("POST", "/vault/v1/admin/manual/reveal", 403, "ACCESS_DENIED");
        VAULT.problem("GET", "/vault/v1/admin/policy-changes/99", 404, "POLICY_ENTRY_NOT_FOUND");
        String update = "{\"expectedVersion\":1,\"dataClass\":\"STANDARD\",\"description\":\"邮箱\",\"manualProtectEnabled\":false,"
            + "\"manualRevealEnabled\":true,\"status\":\"ACTIVE\",\"reason\":\"调整\"}";
        String reveal = "{\"token\":\"" + TOKEN + "\",\"reasonCode\":\"DATA_REPAIR\",\"ticketRef\":\"OPS-2\"}";

        var conflict = send("PUT", "/vault-admin/v1/data-types/EMAIL_ADDRESS_V1", "operations-vault-update-data-type", update);
        var denied = send("POST", "/vault-admin/v1/manual/reveal", "operations-vault-manual-reveal", reveal);
        var missing = send("GET", "/vault-admin/v1/policy-changes/99", "operations-vault-get-policy-change", null);

        assertProblem(conflict, 409, "POLICY_VERSION_CONFLICT");
        assertProblem(denied, 403, "ACCESS_DENIED");
        assertProblem(missing, 404, "POLICY_ENTRY_NOT_FOUND");
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).path("code").asString());
    }

    private static List<String> classes(VaultAdminSimulator.Request call) {
        JsonNode classes = JSON.readTree(call.body()).path("allowedDataClasses");
        return classes.valueStream().map(JsonNode::asString).toList();
    }

    private HttpResponse<String> send(String method, String path, String routeCode, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", routeCode).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Origin", "https://operations-console.local.test");
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        return HTTP.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static VaultAdminSimulator start() {
        try {
            return new VaultAdminSimulator();
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 Vault 管理接口本地 HTTP Simulator", exception);
        }
    }
}
