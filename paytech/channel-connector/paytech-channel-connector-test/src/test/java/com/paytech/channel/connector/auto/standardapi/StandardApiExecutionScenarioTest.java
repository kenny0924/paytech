package com.paytech.channel.connector.auto.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.VaultMocker;

import tools.jackson.databind.node.ObjectNode;

/**
 * 标准 API 执行的协议与边界场景：GET + QUERY + 无请求体 + Bearer 认证 + RESPONSE 脚本改写响应， 表单编码 + Basic 认证，执行前预算耗尽
 * （未发出），非必需初始化项失败与非布尔出边在第一个节点前后的处理， 以及本服务日志不记录凭据与渠道报文。
 *
 * <p>
 * 原子 API 定义、单节点流程（节点 call）、脚本源码与 Vault 账号配置来自 src/test/resources/externalapi， 用例只改动本场景相关的属性。
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiExecutionScenarioTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String CALL = "CALL";
    private static final String HANDLE = "PROTECTED_HANDLE";
    private static final String CHANNEL_MARKER = "channel-only-marker-7f3a";
    private static final String TOKEN = VaultMocker.token("scenario-account");

    private StandardApi pay;
    private ObjectNode account;

    /** 本类公共：标准 API PAY 与合成渠道账号配置；各用例自建渠道、原子 API 与流程。 */
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
        Channel query = integration("QUERY");
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
        Channel form = integration("FORM");
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

    /** 初始化用尽流程预算：未发出 INTEGRATION_BUDGET_EXHAUSTED，渠道没有收到请求。 */
    @Test
    void test_budgetExhaustedBeforeFirstNode() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order").put("timeoutMs", 10);
        createOrderIntegration("SLOW", flow);
        allowAccount();
        vault.delay(TOKEN, 100);

        assertNotSent(StandardApiNotSentCode.INTEGRATION_BUDGET_EXHAUSTED, () -> execute("SLOW"));
        assertTrue(channel.requests().isEmpty());
    }

    /** 非必需初始化项失败不是未发出失败；节点必填的受控句柄因此缺失，节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_optionalInitializerFailure() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order");
        ((ObjectNode)flow.get("initializers").get(0)).put("required", false);
        createOrderIntegration("OPTIONAL", flow);
        vault.fail(TOKEN, 503);

        assertError(execute("OPTIONAL"));
        assertAll(() -> assertEquals(1, vault.revealCount()),
            () -> assertTrue(channel.requests().isEmpty()));
    }

    /** 第一个节点已发送后出边条件不是布尔值：整次结果 ERROR，而不是未发出失败。 */
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

    /** 以样例下单 API（无脚本）和指定流程实现 PAY 的渠道。 */
    private void createOrderIntegration(String code, ObjectNode flow) {
        Channel integration = integration(code);
        externalApi.integrationApi(integration, CALL, ExternalApiSamples.createOrderDefinition());
        externalApi.implementation(integration, pay, flow);
    }

    /** 出站地址指向 Mock 渠道的渠道。 */
    private Channel integration(String code) {
        return externalApi.channel(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
    }

    /** 以合成支付上下文经进程内入口执行 PAY。 */
    private ExecutionResult execute(String integrationCode) {
        return execute(PAY, integrationCode, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}}""".formatted(attempt(), TOKEN)));
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    /** 账号配置中的合成凭据。 */
    private String credential(String name) {
        return account.path("credentials").path(name).asString();
    }

    /** 登记合成渠道账号配置。 */
    private void allowAccount() {
        vault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** SUCCESS 且输出为 PAID 与指定渠道流水号。 */
    private static void assertSuccess(ExecutionResult result, String transactionRef) {
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"PAID","transactionRef":"%s"}""".formatted(transactionRef)), outputs(result)));
    }

    /** 节点之后的失败：执行结果为 ERROR 且输出为空对象。 */
    private static void assertError(ExecutionResult result) {
        assertAll(() -> assertEquals(ExecuteStatus.ERROR, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("{}"), outputs(result)));
    }

    /** 必填 STRING 字段。 */
    private static ApiFieldBody field(ApiFieldLocation location, String path) {
        return ExternalApiSamples.field(location, path, ApiFieldDataType.STRING, true);
    }
}
