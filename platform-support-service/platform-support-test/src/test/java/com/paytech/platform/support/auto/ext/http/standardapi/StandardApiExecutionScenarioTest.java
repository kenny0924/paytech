package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.enums.*;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mock.external.ChannelMockController;
import com.paytech.platform.support.test.mocker.external.ChannelAccountVaultMocker;

import tools.jackson.databind.node.ObjectNode;

/**
 * 标准 API 执行的协议与边界场景：GET + QUERY + 无请求体 + Bearer 认证 + RESPONSE 脚本改写响应， 表单编码 + Basic 认证，执行前预算耗尽
 * 503，非必需初始化项失败与非布尔出边在第一个节点前后的处理， 以及 Platform 自有日志不记录凭据与渠道报文。
 *
 * <p>
 * 原子 API 定义、单节点流程（节点 call）、脚本源码与 Vault 账号配置来自 src/test/resources/externalapi， 用例只改动本场景相关的属性。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiExecutionScenarioTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String CALL = "CALL";
    private static final String HANDLE = "PROTECTED_HANDLE";
    private static final String CHANNEL_MARKER = "channel-only-marker-7f3a";
    private static final String TOKEN = ChannelAccountVaultMocker.token("scenario-account");

    private StandardApi pay;
    private ObjectNode account;

    /** 本类公共：标准 API PAY 与合成渠道账号配置；各用例自建接入方、原子 API 与流程。 */
    @BeforeEach
    void setUpClassMocks() {
        pay = externalApi.standardApi(PAY);
        account = ExternalApiFixtures.channelAccount();
    }

    /**
     * GET 查询：路径变量与固定 QUERY 值按百分号编码、无请求体，REQUEST 脚本以 bearerAuth 写入认证 Header， RESPONSE 脚本用 json.get/json.set
     * 把嵌套状态提到顶层供状态规则判断；日志中没有凭据和渠道报文。
     */
    @Test
    void test_getWithBearerAndResponseScript(CapturedOutput output) {
        Integration query = integration("QUERY");
        externalApi.script(query, "BEARER", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("token", HANDLE, true)), ExternalApiFixtures.script("bearer"));
        externalApi.script(query, "FLATTEN", ScriptExecutionStage.RESPONSE, List.of(),
            ExternalApiFixtures.script("flatten"));
        externalApi.integrationApi(query, CALL, ExternalApiFixtures.definition("query-order"),
            List.of(field(ApiFieldLocation.PATH, "orderRef"), field(ApiFieldLocation.QUERY, "expand")),
            List.of(ExternalApiSamples.field(ApiFieldLocation.BODY, "data", ApiFieldDataType.OBJECT, false),
                field(ApiFieldLocation.BODY, "data.reference")));
        externalApi.implementation(query, pay, ExternalApiFixtures.flow("call-query-order"));
        allowAccount();
        channel.respond("GET", "/api/orders/A%20B%3F" + attempt(), 200, """
            {"data":{"status":"PAID","reference":"ref-q","note":"%s"}}""".formatted(CHANNEL_MARKER));

        assertSuccess(execute("QUERY"), "ref-q");
        var request = channel.only();
        assertAll(() -> assertEquals("expand=detail%20view", request.query()),
            () -> assertEquals("Bearer " + credential("apiKey"), request.headers().get("authorization")),
            () -> assertEquals("", request.body()),
            () -> assertFalse(output.getAll().contains(credential("apiKey")), "日志包含凭据"),
            () -> assertFalse(output.getAll().contains(CHANNEL_MARKER), "日志包含渠道响应报文"));
    }

    /** 表单编码：BODY 字段编码为 application/x-www-form-urlencoded，basicAuth 以两个受控句柄生成认证 Header。 */
    @Test
    void test_formWithBasicAuth() {
        Integration form = integration("FORM");
        externalApi.script(form, "BASIC", ScriptExecutionStage.REQUEST, List
            .of(new ParameterDeclaration("username", HANDLE, true), new ParameterDeclaration("password", HANDLE, true)),
            ExternalApiFixtures.script("basic"));
        externalApi.integrationApi(form, CALL, ExternalApiFixtures.definition("form-refund"),
            List.of(field(ApiFieldLocation.BODY, "reason"), field(ApiFieldLocation.BODY, "amount")),
            List.of(field(ApiFieldLocation.BODY, "reference")));
        externalApi.implementation(form, pay, ExternalApiFixtures.flow("call-form-refund"));
        allowAccount();
        channel.respond("POST", "/api/refunds", 201, "{\"reference\":\"ref-f\"}");

        assertSuccess(execute("FORM"), "ref-f");
        var request = channel.only();
        String basic = credential("username") + ":" + credential("apiKey");
        assertAll(() -> assertEquals("reason=customer%20request&amount=10.00", request.body()),
            () -> assertEquals("application/x-www-form-urlencoded", request.headers().get("content-type")),
            () -> assertEquals("Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)),
                request.headers().get("authorization")));
    }

    /** 初始化用尽流程预算：503 INTEGRATION_BUDGET_EXHAUSTED，渠道没有收到请求。 */
    @Test
    void test_budgetExhaustedBeforeFirstNode() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order").put("timeoutMs", 10);
        createOrderIntegration("SLOW", flow);
        allowAccount();
        channelAccountVault.delay(TOKEN, 100);

        execute("SLOW").assertProblem(503, StandardApiExecutionErrorCode.INTEGRATION_BUDGET_EXHAUSTED);
        assertTrue(channel.requests().isEmpty());
    }

    /** 非必需初始化项失败不返回 Problem；节点必填的受控句柄因此缺失，节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_optionalInitializerFailure() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order");
        ((ObjectNode)flow.get("initializers").get(0)).put("required", false);
        createOrderIntegration("OPTIONAL", flow);
        channelAccountVault.fail(TOKEN, 503);

        assertError(execute("OPTIONAL"));
        assertAll(() -> assertEquals(1, channelAccountVault.revealCount()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 第一个节点已发送后出边条件不是布尔值：整次 200 + ERROR，而不是 Problem。 */
    @Test
    void test_nonBooleanEdgeAfterFirstNode() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order");
        ((ObjectNode)flow.get("edges").get(0)).put("condition", "steps.call.executeStatus");
        createOrderIntegration("EDGE", flow);
        allowAccount();
        channel.respond("POST", "/api/outlets/" + account.path("parameters").path("outletId").asString() + "/orders",
            201, "{\"reference\":\"r\",\"state\":\"CAPTURED\"}");

        assertError(execute("EDGE"));
        assertEquals(1, channel.requests().size());
    }

    /** 以样例下单 API（无脚本）和指定流程实现 PAY 的接入方。 */
    private void createOrderIntegration(String code, ObjectNode flow) {
        Integration integration = integration(code);
        externalApi.integrationApi(integration, CALL, ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(integration, pay, flow);
    }

    private Integration integration(String code) {
        return externalApi.integration(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
    }

    private PlatformResponseChecker execute(String integrationCode) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + PAY + "/execute",
            new ExecuteStandardApiRequestBody(integrationCode, ExternalApiSamples.object("""
                {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}}""".formatted(attempt(),
                TOKEN))));
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    private String credential(String name) {
        return account.path("credentials").path(name).asString();
    }

    private void allowAccount() {
        channelAccountVault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    private static void assertSuccess(PlatformResponseChecker checker, String transactionRef) {
        var body = checker.assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"PAID","transactionRef":"%s"}""".formatted(transactionRef)), body.getOutputs()));
    }

    private static void assertError(PlatformResponseChecker checker) {
        var body = checker.assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.ERROR.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("{}"), body.getOutputs()));
    }

    private static ApiFieldBody field(ApiFieldLocation location, String path) {
        return ExternalApiSamples.field(location, path, ApiFieldDataType.STRING, true);
    }
}
