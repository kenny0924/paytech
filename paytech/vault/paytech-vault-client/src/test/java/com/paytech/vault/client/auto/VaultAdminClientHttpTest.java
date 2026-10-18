package com.paytech.vault.client.auto;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.vault.client.VaultClient;
import com.paytech.vault.client.VaultClientErrorCode;
import com.paytech.vault.client.admin.VaultAdminClient;
import com.paytech.vault.client.admin.VaultAdminClient.AccessRuleFilter;
import com.paytech.vault.client.admin.VaultAdminClient.ManualEventFilter;
import com.paytech.vault.client.admin.request.CreateDataTypeRequestBody;
import com.paytech.vault.client.admin.request.ManualProtectRequestBody;
import com.paytech.vault.client.admin.request.ManualRevealRequestBody;
import com.paytech.vault.client.admin.request.UpdateDataTypeRequestBody;
import com.paytech.vault.client.test.ClientContext;
import com.paytech.vault.client.test.VaultSimulator;

/** 管理接口客户端：开关、协议头、路径与查询参数、成功响应校验、值格式字段往返、新错误码映射。 */
class VaultAdminClientHttpTest {
    private static final String TOKEN = VaultClientSafetyTest.TOKEN;
    private static final String OPERATOR = "operator-42";
    private static final String PROBLEM = "application/problem+json";
    private static final String TIMESTAMP = "2026-10-15T10:20:30.123456Z";

    private static String problem(int status, String title, String code, String requestId) {
        return "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"detail\":\"safe detail\",\"code\":\"" + code
            + "\",\"requestId\":\"" + requestId + "\",\"timestamp\":\"" + TIMESTAMP + "\"}";
    }

    @Test
    void test_adminClientIsOffByDefault() throws Exception {
        try (var simulator = new VaultSimulator()) {
            ClientContext.direct(simulator).run(context -> {
                assertThat(context).hasSingleBean(VaultClient.class);
                assertThat(context).doesNotHaveBean(VaultAdminClient.class);
            });
        }
    }

    @Test
    void test_manualProtectSendsOperatorAndSubmissionIdOnly() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"token\":\"" + TOKEN + "\"}");
            adminContext(simulator).run(context -> {
                var body = new ManualProtectRequestBody();
                body.setDataTypeCode("CHANNEL_ACCOUNT_CONFIG_V1");
                body.setValue("synthetic-secret");
                body.setReasonCode("CHANNEL_CONFIGURATION");
                body.setTicketRef("OPS-1");
                body.setAllowedDataClasses(List.of("STANDARD", "SECRET"));
                var result = context.getBean(VaultAdminClient.class).manualProtect(OPERATOR, "submission-1", body);
                assertThat(result.getToken()).isEqualTo(TOKEN);
            });
            var request = simulator.requests.getFirst();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/vault/v1/admin/manual/protect");
            assertThat(request.headers().get("X-Caller-Service")).containsExactly("operations-console-bff");
            assertThat(request.headers().get("X-Operator-Id")).containsExactly(OPERATOR);
            assertThat(request.headers().get("Idempotency-Key")).containsExactly("submission-1");
            assertThat(request.body()).contains("\"allowedDataClasses\":[\"STANDARD\",\"SECRET\"]");
        }
    }

    @Test
    void test_manualRevealValidatesResponseShape() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"dataTypeCode\":\"PHONE_E164_V1\",\"value\":\"+971500000001\"}");
            simulator.respond(200, "{\"dataTypeCode\":\"PHONE_E164_V1\",\"value\":\"x\",\"extra\":1}");
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                var revealed = client.manualReveal(OPERATOR, revealBody());
                assertThat(revealed.getDataTypeCode()).isEqualTo("PHONE_E164_V1");
                assertThat(revealed.getValue()).isEqualTo("+971500000001");
                var failure = catchThrowableOfType(ErrorException.class, () -> client.manualReveal(OPERATOR, revealBody()));
                assertThat(failure.getErrorCode()).isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
                assertThat(failure.getMessage()).doesNotContain("PHONE_E164_V1");
            });
            assertThat(simulator.requests.getFirst().headers().get("Idempotency-Key")).isNull();
        }
    }

    @Test
    void test_queriesCarryFiltersAndDecodePages() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, "{\"items\":[{\"accessRuleId\":\"9007199254740993\",\"callerService\":\"commerce-service\","
                + "\"action\":\"REVEAL\",\"dataTypeCode\":\"EMAIL_ADDRESS_V1\",\"purposeCode\":\"CHECKOUT_CONTACT\","
                + "\"status\":\"ACTIVE\",\"version\":2,\"updateTime\":\"" + TIMESTAMP + "\"}],\"page\":2,\"size\":10,\"total\":11}");
            simulator.respond(200, "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}");
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                var rules = client.listAccessRules(OPERATOR,
                    new AccessRuleFilter("EMAIL_ADDRESS_V1", null, "REVEAL", null), 2, 10);
                assertThat(rules.total()).isEqualTo(11);
                assertThat(rules.items().getFirst().getAccessRuleId()).isEqualTo("9007199254740993");
                assertThat(rules.items().getFirst().getUpdateTime()).isEqualTo(Instant.parse(TIMESTAMP));
                client.listManualEvents(OPERATOR, new ManualEventFilter(null, null, null,
                    Instant.parse("2026-10-01T00:00:00Z"), null), null, null);
            });
            assertThat(simulator.requests.get(0).path())
                .isEqualTo("/vault/v1/admin/access-rules?dataTypeCode=EMAIL_ADDRESS_V1&action=REVEAL&page=2&size=10");
            assertThat(simulator.requests.get(0).headers().get("Idempotency-Key")).isNull();
            assertThat(java.net.URLDecoder.decode(simulator.requests.get(1).path(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo("/vault/v1/admin/manual-events?from=2026-10-01T00:00:00Z");
        }
    }

    @Test
    void test_policyErrorCodesMapToFailures() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(409, problem(409, "Conflict", "POLICY_VERSION_CONFLICT", "r-1"), PROBLEM, List.of("r-1"));
            simulator.respond(404, problem(404, "Not Found", "POLICY_ENTRY_NOT_FOUND", "r-2"), PROBLEM, List.of("r-2"));
            simulator.respond(404, problem(404, "Not Found", "POLICY_VERSION_CONFLICT", "r-3"), PROBLEM, List.of("r-3"));
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                var update = new UpdateDataTypeRequestBody();
                update.setExpectedVersion(1);
                assertThat(failureCode(() -> withRequestId("r-1", () -> client.updateDataType(OPERATOR, "EMAIL_ADDRESS_V1", update))))
                    .isEqualTo(VaultClientErrorCode.POLICY_VERSION_CONFLICT.getCode());
                assertThat(failureCode(() -> withRequestId("r-2", () -> client.getPolicyChange(OPERATOR, 7))))
                    .isEqualTo(VaultClientErrorCode.POLICY_ENTRY_NOT_FOUND.getCode());
                var mismatch = catchThrowableOfType(ErrorException.class,
                    () -> withRequestId("r-3", () -> client.getPolicyChange(OPERATOR, 8)));
                assertThat(mismatch.getErrorCode()).isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
            });
            assertThat(simulator.requests.get(0).path()).isEqualTo("/vault/v1/admin/data-types/EMAIL_ADDRESS_V1");
            assertThat(simulator.requests.get(0).method()).isEqualTo("PUT");
        }
    }

    @Test
    void test_dataTypeValueFormatFieldsRoundTrip() throws Exception {
        try (var simulator = new VaultSimulator()) {
            simulator.respond(200, dataTypeResponse("\"valueFormat\":\"REGEX\",\"valuePattern\":\"\\\\+[1-9][0-9]{7,14}\""));
            simulator.respond(200, dataTypeResponse("\"valueFormat\":\"JSON\""));
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                var create = new CreateDataTypeRequestBody();
                create.setDataTypeCode("PHONE_E164_V1");
                create.setValueFormat("REGEX");
                create.setValuePattern("\\+[1-9][0-9]{7,14}");
                create.setSampleValues(List.of("+971500000001"));
                var created = client.createDataType(OPERATOR, create).getDataType();
                assertThat(created.getValueFormat()).isEqualTo("REGEX");
                assertThat(created.getValuePattern()).isEqualTo("\\+[1-9][0-9]{7,14}");

                var update = new UpdateDataTypeRequestBody();
                update.setExpectedVersion(1);
                update.setValueFormat("JSON");
                var updated = client.updateDataType(OPERATOR, "PHONE_E164_V1", update).getDataType();
                assertThat(updated.getValueFormat()).isEqualTo("JSON");
                assertThat(updated.getValuePattern()).isNull();
            });
            assertThat(simulator.requests.get(0).body())
                .contains("\"valueFormat\":\"REGEX\"", "\"valuePattern\":\"\\\\+[1-9][0-9]{7,14}\"",
                    "\"sampleValues\":[\"+971500000001\"]");
            assertThat(simulator.requests.get(1).body()).contains("\"valueFormat\":\"JSON\"");
        }
    }

    @Test
    void test_valueFormatErrorCodesMapToBadRequestFailures() throws Exception {
        var codes = List.of(VaultClientErrorCode.VALUE_PATTERN_INVALID, VaultClientErrorCode.SAMPLE_VALUE_INVALID,
            VaultClientErrorCode.VALUE_NOT_JSON_OBJECT, VaultClientErrorCode.VALUE_PATTERN_MISMATCH);
        try (var simulator = new VaultSimulator()) {
            for (int index = 0; index < codes.size(); index++) {
                String requestId = "f-" + index;
                simulator.respond(400, problem(400, "Bad Request", codes.get(index).getCode(), requestId), PROBLEM,
                    List.of(requestId));
            }
            simulator.respond(409, problem(409, "Conflict", VaultClientErrorCode.VALUE_PATTERN_MISMATCH.getCode(), "f-9"),
                PROBLEM, List.of("f-9"));
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                for (int index = 0; index < codes.size(); index++) {
                    String requestId = "f-" + index;
                    var expected = codes.get(index);
                    var rejected = catchThrowableOfType(FailException.class,
                        () -> withRequestId(requestId, () -> client.manualProtect(OPERATOR, "submission-" + requestId,
                            new ManualProtectRequestBody())));
                    assertThat(rejected.getCode()).isEqualTo(expected.getCode());
                    assertThat(rejected.getMessage()).startsWith(expected.getMessage()).doesNotContain("safe detail");
                }
                var mismatch = catchThrowableOfType(ErrorException.class,
                    () -> withRequestId("f-9", () -> client.manualProtect(OPERATOR, "submission-f-9",
                        new ManualProtectRequestBody())));
                assertThat(mismatch.getErrorCode()).isSameAs(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
            });
            assertThat(simulator.requests).hasSize(codes.size() + 1);
        }
    }

    @Test
    void test_invalidLocalInputIsRejectedWithoutRequest() throws Exception {
        try (var simulator = new VaultSimulator()) {
            adminContext(simulator).run(context -> {
                var client = context.getBean(VaultAdminClient.class);
                assertThat(failureCode(() -> client.listDataTypes("bad operator", null)))
                    .isEqualTo(VaultClientErrorCode.INVALID_REQUEST.getCode());
                assertThat(failureCode(() -> client.manualProtect(OPERATOR, "bad key!", new ManualProtectRequestBody())))
                    .isEqualTo(VaultClientErrorCode.INVALID_REQUEST.getCode());
                assertThat(failureCode(() -> client.updateDataType(OPERATOR, "../x", new UpdateDataTypeRequestBody())))
                    .isEqualTo(VaultClientErrorCode.INVALID_REQUEST.getCode());
                assertThat(failureCode(() -> client.updateAccessRule(OPERATOR, "0", null)))
                    .isEqualTo(VaultClientErrorCode.INVALID_REQUEST.getCode());
            });
            assertThat(simulator.requests).isEmpty();
        }
    }

    private static org.springframework.boot.test.context.runner.ApplicationContextRunner adminContext(
        VaultSimulator simulator) {
        return ClientContext.direct(simulator).withPropertyValues("spring.application.name=operations-console-bff",
            "vault.client.admin-enabled=true");
    }

    private static String dataTypeResponse(String formatMembers) {
        return "{\"dataType\":{\"dataTypeCode\":\"PHONE_E164_V1\",\"tokenMode\":\"STABLE\",\"dataClass\":\"STANDARD\","
            + "\"description\":\"synthetic\"," + formatMembers + ",\"manualProtectEnabled\":false,"
            + "\"manualRevealEnabled\":false,\"status\":\"ACTIVE\",\"version\":2,\"updateTime\":\"" + TIMESTAMP
            + "\"},\"policyVersion\":5}";
    }

    private static ManualRevealRequestBody revealBody() {
        var body = new ManualRevealRequestBody();
        body.setToken(TOKEN);
        body.setReasonCode("CUSTOMER_VERIFICATION");
        body.setTicketRef("OPS-2");
        body.setAllowedDataClasses(List.of("STANDARD"));
        return body;
    }

    private static String failureCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return catchThrowableOfType(FailException.class, call).getCode();
    }

    private static Object withRequestId(String requestId, java.util.function.Supplier<?> call) {
        MDC.put("requestId", requestId);
        try {
            return call.get();
        } finally {
            MDC.clear();
        }
    }
}
