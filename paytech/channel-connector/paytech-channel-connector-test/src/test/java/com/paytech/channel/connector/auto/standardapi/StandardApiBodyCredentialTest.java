package com.paytech.channel.connector.auto.standardapi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.channel.connector.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.channel.connector.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.channel.connector.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.domain.execution.ProtectedHandle;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.dal.VerifyTestMapper;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.VerifyRunFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 请求体凭据字段（Toss Pay 接入技术方案 T1）：原子 API credentialFields 声明的 BODY 字段以 PATH 绑定渠道账号凭据，受控句柄只在发送字节中展开；
 * 标准 API 执行结果与日志不含明文，沙箱验证证据与库中轨迹为 [REDACTED]。请求体含凭据时 sealJsonBody 使节点 ERROR，脚本把受控句柄写到其他请求体字段
 * 也使节点 ERROR，两者都不发出渠道请求。
 *
 * <p>
 * 渠道是测试 Mock HTTP 服务，凭据为合成值。执行入口为 protocol 公开的进程内标准 API 执行（原 Platform HTTP 执行接口已删除）。
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiBodyCredentialTest extends ExternalApiTestBase {

    private static final String PAY = "BODY_KEY_PAY";
    private static final String CALL = "CALL";
    private static final String CODE = "BODY_KEY";
    private static final String SANDBOX = "BODY_KEY_SANDBOX";
    private static final String PATH = "/api/payments";
    private static final String VAULT_TOKEN = VaultMocker.token("body-key-account");

    @Autowired
    private VerifyRunFixture runs;

    @Autowired
    private VerifyTestMapper verifyMapper;

    private ObjectNode account;
    private StandardApi pay;

    /** 本类公共：支付标准 API、渠道账号（apiKey 凭据）。 */
    @BeforeEach
    void setUpClassMocks() {
        pay = externalApi.standardApi(PAY, List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
                field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
                field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
                field("paymentContext.vaultToken", ApiFieldDataType.STRING, true)),
            List.of(field("paymentStatus", ApiFieldDataType.STRING, true),
                field("transactionRef", ApiFieldDataType.STRING, false)));
        account = ExternalApiFixtures.channelAccount();
        vault.allow(VAULT_TOKEN, ExternalApiSamples.text(account));
    }

    /** 渠道收到请求体中的明文 apiKey；标准 API 执行结果与日志不含明文。 */
    @Test
    void test_credentialOnlyInChannelBytes(CapturedOutput output) {
        bodyKeyIntegration(CODE, false, null);
        channel.respond("POST", PATH, 200, "{\"code\":0,\"payToken\":\"T-BODY\"}");

        var result = execute(CODE);

        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"SUCCEEDED","transactionRef":"T-BODY"}"""), outputs(result)),
            () -> assertEquals("{\"apiKey\":\"%s\",\"orderNo\":\"%s\",\"amount\":10.00}".formatted(apiKey(), attempt()),
                channel.only().body()),
            () -> assertFalse(outputs(result).toString().contains(apiKey()), "执行结果包含凭据"),
            () -> assertFalse(output.getAll().contains(apiKey()), "日志包含凭据"));
    }

    /** 请求体含凭据时 sealJsonBody 使节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_sealRejectedWithCredential() {
        bodyKeyIntegration(CODE, false, "seal-body");

        assertError(execute(CODE));
        assertTrue(channel.requests().isEmpty());
    }

    /** 脚本把受控句柄写到非凭据字段：节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_handleOutsideCredentialField() {
        bodyKeyIntegration(CODE, false, "leak-credential");

        assertError(execute(CODE));
        assertTrue(channel.requests().isEmpty());
    }

    /** 沙箱验证：渠道收到明文，证据与库中轨迹只有遮蔽文本。 */
    @Test
    void test_verifyRunMasksCredentialEvidence() {
        bodyKeyIntegration(SANDBOX, true, null);
        post(BASE + "/integrations/" + SANDBOX + "/verify-accounts", new CreateVerifyAccountRequestBody("MAIN",
            "测试账号", "DEMO", attempt(), attempt(), Map.of(), Map.of("apiKey", apiKey()), "凭据字段验证")).assertOk();
        post(BASE + "/integrations/" + SANDBOX + "/verify-cases", new CreateVerifyCaseRequestBody("BODY_KEY_PAY",
            "下单", null, "MAIN", ExternalApiSamples.object("""
                {"steps":[{"id":"pay","standardApiCode":"%s",
                  "input":{"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}},
                  "expect":{"executeStatus":"SUCCESS"}}]}""".formatted(PAY, attempt(), VAULT_TOKEN)).get("steps"),
            "凭据字段验证")).assertOk();
        channel.respond("POST", PATH, 200, "{\"code\":0,\"payToken\":\"T-VERIFY\"}");

        var started = post(BASE + "/integrations/" + SANDBOX + "/verify-cases/BODY_KEY_PAY/runs",
            new StartVerifyRunRequestBody("凭据字段验证")).assertOk().as(VerifyRunStartResponseBody.class);
        runs.awaitIdle();
        await().atMost(Duration.ofSeconds(5)).until(() -> !VerifyRunStatus.RUNNING.getCode()
            .equals(runDetail(started.getRunCode()).getRunStatus()));
        VerifyRunResponseBody run = runDetail(started.getRunCode());

        JsonNode sent = run.getSteps().getFirst().getTrace().path("nodes").get(0).path("exchange").path("request");
        assertAll(() -> assertEquals(VerifyRunStatus.PASSED.getCode(), run.getRunStatus()),
            () -> assertEquals("{\"apiKey\":\"%s\",\"orderNo\":\"%s\",\"amount\":10.00}".formatted(ProtectedHandle.MASK,
                attempt()), sent.path("body").path("text").asString()),
            () -> assertTrue(channel.only().body().contains(apiKey())),
            () -> assertFalse(verifyMapper.traces(Long.parseLong(started.getRunCode())).stream()
                .anyMatch(trace -> trace != null && trace.contains(apiKey())), "轨迹包含凭据"));
    }

    /** 接入方：body apiKey 下单原子 API（可选一个 REQUEST 脚本）与流程。 */
    private void bodyKeyIntegration(String code, boolean sandbox, String requestScript) {
        Integration integration =
            externalApi.integration(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api", sandbox);
        ObjectNode definition = ExternalApiFixtures.definition("body-credential-order");
        if (requestScript != null) {
            externalApi.script(integration, "BODY_SCRIPT", ScriptExecutionStage.REQUEST,
                List.of(new ParameterDeclaration("key", "PROTECTED_HANDLE", true)), ExternalApiFixtures.script(requestScript));
            definition.withArray("scripts").add(ExternalApiSamples.object("""
                {"id":"script","scriptCode":"BODY_SCRIPT","stage":"REQUEST",
                 "params":{"key":{"kind":"PATH","path":"params.apiKey"}}}"""));
        }
        externalApi.integrationApi(integration, CALL, definition,
            List.of(field("apiKey", ApiFieldDataType.STRING, true), field("orderNo", ApiFieldDataType.STRING, true),
                field("amount", ApiFieldDataType.DECIMAL, true), field("note", ApiFieldDataType.STRING, false)),
            List.of(field("code", ApiFieldDataType.INTEGER, true), field("payToken", ApiFieldDataType.STRING, false)));
        externalApi.implementation(integration, pay, ExternalApiFixtures.flow("call-body-credential-order"));
    }

    /** 以合成支付上下文经进程内入口执行标准 API。 */
    private ExecutionResult execute(String integrationCode) {
        return execute(PAY, integrationCode, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}}""".formatted(attempt(),
            VAULT_TOKEN)));
    }

    /** 沙箱验证运行详情。 */
    private VerifyRunResponseBody runDetail(String runCode) {
        return get(BASE + "/integrations/" + SANDBOX + "/verify-runs/" + runCode).assertOk()
            .as(VerifyRunResponseBody.class);
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    /** 渠道账号配置中的合成 apiKey。 */
    private String apiKey() {
        return account.path("credentials").path("apiKey").asString();
    }

    /** 节点之后的失败：执行结果为 ERROR 且输出为空对象。 */
    private static void assertError(ExecutionResult result) {
        assertAll(() -> assertEquals(ExecuteStatus.ERROR, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("{}"), outputs(result)));
    }

    /** BODY 字段。 */
    private static ApiFieldBody field(String path, ApiFieldDataType type, boolean required) {
        return ExternalApiSamples.field(ApiFieldLocation.BODY, path, type, required);
    }
}
