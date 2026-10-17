package com.paytech.platform.support.auto.ext.http.externalapi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.platform.support.api.enums.OperationsErrorCode;
import com.paytech.platform.support.api.enums.VerifyCaseStatus;
import com.paytech.platform.support.api.enums.VerifyRunStatus;
import com.paytech.platform.support.api.enums.VerifyStepStatus;
import com.paytech.platform.support.api.externalapi.IntegrationVerifyManagementApi;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.platform.support.api.externalapi.request.UpdateIntegrationApiRequestBody;
import com.paytech.platform.support.api.externalapi.response.VerifyCaseResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.dal.VerifyTestMapper;
import com.paytech.platform.support.test.mock.external.ChannelMockController;
import com.paytech.platform.support.test.mocker.local.VerifyRunFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 沙箱验证运行：发起后经真实补偿执行器异步执行，复用正式执行链路调用渠道 Mock，保存步骤、期望核对与脱敏轨迹；
 * 覆盖通过、期望不满足与后续步骤跳过、前序步骤引用、期望执行前问题、出站地址未标记沙箱（渠道零请求）、
 * 配置变更后需重新验证，以及运行列表与权限。
 *
 * @see IntegrationVerifyManagementApi#startVerifyRun(String, String, String, String, StartVerifyRunRequestBody)
 * @see IntegrationVerifyManagementApi#listVerifyRuns(String, String, String, String, Integer, Integer)
 * @see IntegrationVerifyManagementApi#getVerifyRun(String, String, String, String)
 * @see IntegrationVerifyManagementApi#listVerifyCases(String, String, String, Integer, Integer, String)
 * @see IntegrationVerifyManagementApi#getVerifyCase(String, String, String, String)
 */
class VerifyRunTest extends ExternalApiTestBase {

    private static final String SANDBOX = "SANDBOX";
    private static final String ORDERS = "/api/outlets/outlet-1/orders";
    private static final String ATTEMPT = "301000000000000001";
    private static final String CALL = "CALL";
    private static final StartVerifyRunRequestBody START = new StartVerifyRunRequestBody("回归验证");

    @Autowired
    private VerifyRunFixture runs;

    @Autowired
    private VerifyTestMapper verifyMapper;

    private Operator admin;
    private StandardApi pay;

    /** 本类公共：管理员、标准 API PAY、沙箱接入方（下单原子 API + 单节点流程）与验证账号 MAIN。 */
    @BeforeEach
    void setUpClassMocks() {
        admin = administrator();
        pay = externalApi.standardApi("PAY");
        implement(SANDBOX, true);
    }

    /** 两步用例全部通过：第二步引用第一步的输入与输出；轨迹含出站请求与响应，认证类 Header 已脱敏；用例状态 PASSED。 */
    @Test
    void test_passedWithStepReference() {
        createCase(SANDBOX, "TWO_STEPS", "[" + payStep("pay", "T${run.code}", "PAID") + ","
            + payStep("again", "${steps.pay.outputs.transactionRef}", "PAID") + "]");
        channel.respond("POST", ORDERS, 201, "{\"reference\":\"r-1\",\"state\":\"CAPTURED\"}");
        channel.respond("POST", ORDERS, 201, "{\"reference\":\"r-2\",\"state\":\"CAPTURED\"}");

        VerifyRunResponseBody run = runToEnd(SANDBOX, "TWO_STEPS");

        assertAll(() -> assertEquals(VerifyRunStatus.PASSED.getCode(), run.getRunStatus()),
            () -> assertNull(run.getFailureSummary()), () -> assertFalse(run.isStale()),
            () -> assertEquals("回归验证", run.getCaseSnapshot().path("reason").asString()),
            () -> assertEquals(List.of(VerifyStepStatus.PASSED.getCode(), VerifyStepStatus.PASSED.getCode()),
                run.getSteps().stream().map(step -> step.getStepStatus()).toList()),
            () -> assertEquals("T" + run.getRunCode(),
                run.getSteps().getFirst().getResolvedInput().path("paymentContext").path("vaultToken").asString()),
            () -> assertEquals("r-1",
                run.getSteps().get(1).getResolvedInput().path("paymentContext").path("vaultToken").asString()),
            () -> assertEquals("done", run.getSteps().getFirst().getTrace().path("returnId").asString()),
            () -> assertTrue(run.getEndpoints().get(0).path("sandbox").asBoolean()),
            () -> assertEquals(1, run.getConfigVersions().path("pay").path("standardApi").asInt()));
        JsonNode exchange = run.getSteps().getFirst().getTrace().path("nodes").get(0).path("exchange");
        assertAll(() -> assertEquals("POST", exchange.path("request").path("method").asString()),
            () -> assertTrue(exchange.path("request").path("url").asString().endsWith(ORDERS)),
            () -> assertEquals(201, exchange.path("response").path("status").asInt()),
            () -> assertTrue(exchange.path("response").path("body").path("text").asString().contains("r-1")),
            () -> assertEquals(2, channel.requests().size()));
        assertEquals(VerifyCaseStatus.PASSED.getCode(), caseDetail(SANDBOX, "TWO_STEPS").getVerifyStatus());
    }

    /** 第一步期望不满足：运行 FAILED，失败摘要指出路径，第二步 SKIPPED 且不发请求。 */
    @Test
    void test_expectationMismatchSkipsRest() {
        createCase(SANDBOX, "MISMATCH", "[" + payStep("pay", "T1", "DECLINED") + "," + payStep("again", "T2", "PAID") + "]");
        channel.respond("POST", ORDERS, 201, "{\"reference\":\"r-1\",\"state\":\"CAPTURED\"}");

        VerifyRunResponseBody run = runToEnd(SANDBOX, "MISMATCH");

        assertAll(() -> assertEquals(VerifyRunStatus.FAILED.getCode(), run.getRunStatus()),
            () -> assertTrue(run.getFailureSummary().contains("outputs.paymentStatus"), run.getFailureSummary()),
            () -> assertEquals(VerifyStepStatus.FAILED.getCode(), run.getSteps().getFirst().getStepStatus()),
            () -> assertEquals(VerifyStepStatus.SKIPPED.getCode(), run.getSteps().get(1).getStepStatus()),
            () -> assertTrue(run.getSteps().get(1).getResult() == null || run.getSteps().get(1).getResult().isNull()),
            () -> assertEquals(1, channel.requests().size()));
        assertEquals(VerifyCaseStatus.FAILED.getCode(), caseDetail(SANDBOX, "MISMATCH").getVerifyStatus());
    }

    /** 负向用例：缺少必填输入时期望 422 VALIDATION_ERROR，运行通过且渠道零请求。 */
    @Test
    void test_expectedProblem() {
        createCase(SANDBOX, "INVALID_INPUT", """
            [{"id":"pay","standardApiCode":"PAY","input":{"paymentContext":{"attemptId":"%s","vaultToken":"T"}},
              "expect":{"problem":{"status":422,"code":"VALIDATION_ERROR"}}}]""".formatted(ATTEMPT));

        VerifyRunResponseBody run = runToEnd(SANDBOX, "INVALID_INPUT");

        assertAll(() -> assertEquals(VerifyRunStatus.PASSED.getCode(), run.getRunStatus()),
            () -> assertEquals("PROBLEM", run.getSteps().getFirst().getResult().path("kind").asString()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 出站地址未标记沙箱：步骤在任何外部调用前失败，渠道零请求，运行 FAILED。 */
    @Test
    void test_notSandboxNeverCallsChannel() {
        implement("PRODUCTION", false);
        createCase("PRODUCTION", "PAY_ONCE", "[" + payStep("pay", "T1", "PAID") + "]");

        VerifyRunResponseBody run = runToEnd("PRODUCTION", "PAY_ONCE");

        assertAll(() -> assertEquals(VerifyRunStatus.FAILED.getCode(), run.getRunStatus()),
            () -> assertEquals("NOT_EXECUTED", run.getSteps().getFirst().getResult().path("kind").asString()),
            () -> assertTrue(run.getFailureSummary().contains("未标记沙箱"), run.getFailureSummary()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 通过后修改引用的原子 API：运行详情与用例状态变为需重新验证；运行列表按用例筛选。 */
    @Test
    void test_staleAfterConfigChangeAndRunList() {
        createCase(SANDBOX, "PAY_ONCE", "[" + payStep("pay", "T1", "PAID") + "]");
        channel.respond("POST", ORDERS, 201, "{\"reference\":\"r-1\",\"state\":\"CAPTURED\"}");
        VerifyRunResponseBody run = runToEnd(SANDBOX, "PAY_ONCE");
        assertEquals(VerifyRunStatus.PASSED.getCode(), run.getRunStatus());

        var definition = ExternalApiSamples.createOrderDefinition();
        ((ObjectNode)definition.get("transport")).put("responseTimeoutMs", 9000);
        put(BASE + "/integrations/" + SANDBOX + "/apis/" + CALL, admin,
            new UpdateIntegrationApiRequestBody("下单", null, ExternalApiSamples.createOrderRequestFields(),
                ExternalApiSamples.createOrderResponseFields(), definition, 1, "调整超时")).assertOk();

        assertAll(() -> assertTrue(runDetail(SANDBOX, run.getRunCode()).isStale()),
            () -> assertEquals(VerifyCaseStatus.STALE.getCode(), caseDetail(SANDBOX, "PAY_ONCE").getVerifyStatus()));
        JsonNode page = get(BASE + "/integrations/" + SANDBOX + "/verify-runs?caseCode=PAY_ONCE", admin).assertOk().json();
        assertAll(() -> assertEquals(1, page.path("total").asInt()),
            () -> assertEquals(run.getRunCode(), page.path("items").get(0).path("runCode").asString()));
        JsonNode cases = get(BASE + "/integrations/" + SANDBOX + "/verify-cases", admin).assertOk().json();
        assertEquals(VerifyCaseStatus.STALE.getCode(), cases.path("items").get(0).path("verifyStatus").asString());
    }

    /** 只读人员不能发起运行；不存在的运行编号与他人接入方的运行均为 404。 */
    @Test
    void test_permissionAndNotFound() {
        createCase(SANDBOX, "PAY_ONCE", "[" + payStep("pay", "T1", "PAID") + "]");
        post(BASE + "/integrations/" + SANDBOX + "/verify-cases/PAY_ONCE/runs", reader(), START)
            .assertProblem(403, OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        get(BASE + "/integrations/" + SANDBOX + "/verify-runs/123", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        get(BASE + "/integrations/" + SANDBOX + "/verify-runs/abc", admin).assertProblem(404, CommonErrorCode.NOT_FOUND);
        post(BASE + "/integrations/" + SANDBOX + "/verify-cases/MISSING/runs", admin, START)
            .assertProblem(404, CommonErrorCode.NOT_FOUND);
    }

    /** 接入方实现 PAY：下单原子 API CALL 与单节点流程，验证账号 MAIN 与流程身份一致。 */
    private void implement(String code, boolean sandbox) {
        Integration integration =
            externalApi.integration(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api", sandbox);
        externalApi.integrationApi(integration, CALL, ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(integration, pay, ExternalApiFixtures.flow("call-create-order"));
        post(BASE + "/integrations/" + code + "/verify-accounts", admin, new CreateVerifyAccountRequestBody("MAIN",
            "主账号", "DEMO", ATTEMPT, ATTEMPT, Map.of("outletId", "outlet-1"), Map.of("apiKey", "sandbox-key"),
            "沙箱验证")).assertOk();
    }

    private void createCase(String integration, String code, String steps) {
        post(BASE + "/integrations/" + integration + "/verify-cases", admin, new CreateVerifyCaseRequestBody(code,
            "用例", null, "MAIN", ExternalApiSamples.object("{\"steps\":" + steps + "}").get("steps"), "沙箱验证"))
            .assertOk();
    }

    /** 一个调用 PAY 的步骤，期望 SUCCESS 与指定支付状态。 */
    private static String payStep(String id, String vaultToken, String paymentStatus) {
        return """
            {"id":"%s","standardApiCode":"PAY",
             "input":{"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}},
             "expect":{"executeStatus":"SUCCESS","returnId":"done",
               "outputs":[{"path":"paymentStatus","match":"EQUALS","value":"%s"},
                          {"path":"transactionRef","match":"EXISTS"}]}}""".formatted(id, ATTEMPT, vaultToken,
            paymentStatus);
    }

    /** 发起运行并等待补偿执行器写入结论。 */
    private VerifyRunResponseBody runToEnd(String integration, String caseCode) {
        var started = post(BASE + "/integrations/" + integration + "/verify-cases/" + caseCode + "/runs", admin, START)
            .assertOk().as(VerifyRunStartResponseBody.class);
        assertEquals(VerifyRunStatus.RUNNING.getCode(), started.getRunStatus());
        runs.awaitIdle();
        await().atMost(Duration.ofSeconds(5)).until(() -> !VerifyRunStatus.RUNNING.getCode()
            .equals(runDetail(integration, started.getRunCode()).getRunStatus()));
        assertFalse(verifyMapper.traces(Long.parseLong(started.getRunCode())).stream()
            .anyMatch(trace -> trace != null && trace.contains("sandbox-key")), "轨迹包含凭据");
        return runDetail(integration, started.getRunCode());
    }

    private VerifyRunResponseBody runDetail(String integration, String runCode) {
        return get(BASE + "/integrations/" + integration + "/verify-runs/" + runCode, admin).assertOk()
            .as(VerifyRunResponseBody.class);
    }

    private VerifyCaseResponseBody caseDetail(String integration, String caseCode) {
        return get(BASE + "/integrations/" + integration + "/verify-cases/" + caseCode, admin).assertOk()
            .as(VerifyCaseResponseBody.class);
    }
}
