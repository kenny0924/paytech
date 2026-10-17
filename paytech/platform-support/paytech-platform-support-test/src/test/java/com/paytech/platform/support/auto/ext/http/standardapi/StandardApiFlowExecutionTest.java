package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.enums.ExecuteStatus;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.mock.external.ChannelMockController;
import com.paytech.platform.support.test.mocker.external.ChannelAccountVaultMocker;
import com.paytech.platform.support.test.mocker.external.ChannelMocker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 标准 API 完整执行：初始化项经 Vault 读取渠道账号，下单节点组装请求、REQUEST 脚本以受控句柄签名、 发送到本机 Mock 渠道，按状态规则得到 SUCCESS / FAIL / ERROR 并映射出口。
 *
 * <p>
 * 第一个节点开始后的渠道错误、断连与超时都是 200 + ERROR；初始化失败是 503，且渠道没有收到请求。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
class StandardApiFlowExecutionTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String DEMO = "DEMO";
    private static final String OUTLET = "outlet 1";
    private static final String ATTEMPT = ExternalApiFixtures.channelAccount().path("channelAccountId").asString();
    private static final String API_KEY =
        ExternalApiFixtures.channelAccount().path("credentials").path("apiKey").asString();
    private static final String ORDERS = "/api/outlets/outlet%201/orders";
    private static final String TOKEN = ChannelAccountVaultMocker.token("demo-account");

    private StandardApi pay;
    private Integration demo;

    /** 本类公共：标准 API PAY；接入方 DEMO 指向 Mock 渠道，下单 API 引用签名脚本，以样例支付流程实现 PAY。 */
    @BeforeEach
    void setUpClassMocks() {
        pay = externalApi.standardApi(PAY);
        demo = externalApi.integration(DEMO, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
        externalApi.signScript(demo, ScriptExecutionStage.REQUEST);
        externalApi.integrationApi(demo, ExternalApiSamples.CREATE_ORDER,
            ExternalApiSamples.createOrderDefinitionWithScript(ExternalApiSamples.SIGN_SCRIPT));
        externalApi.implementation(demo, pay, ExternalApiSamples.payFlow());
    }

    /** 渠道 201 CAPTURED：SUCCESS，请求路径按段编码、版本头来自固定值、Content-Type 按 JSON 编码补默认值、签名由脚本用受控句柄计算。 */
    @Test
    void test_success() {
        allowAccount(DEMO);
        channel.respond("POST", ORDERS, 201, "{\"reference\":\"ref-1\",\"state\":\"CAPTURED\"}");

        assertResult(ExecuteStatus.SUCCESS, Map.of("paymentStatus", "PAID", "transactionRef", "ref-1"), execute());
        var request = channel.only();
        assertAll(() -> assertEquals("application/json", request.headers().get("content-type")),
            () -> assertEquals("2", request.headers().get("x-api-version")),
            () -> assertEquals(hmac(API_KEY, ATTEMPT), request.headers().get("signature")),
            () -> assertEquals("{\"orderRef\":\"" + ATTEMPT + "\",\"amount\":\"10.00\"}", request.body()),
            () -> assertEquals(1, channelAccountVault.revealCount()));
    }

    /** 渠道 201 DECLINED：FAIL，走拒绝出口。 */
    @Test
    void test_declined() {
        allowAccount(DEMO);
        channel.respond("POST", ORDERS, 201, "{\"state\":\"DECLINED\",\"errorCode\":\"E51\"}");

        assertResult(ExecuteStatus.FAIL, Map.of("paymentStatus", "DECLINED"), execute());
    }

    /** 状态规则全部为假（渠道 500）、成功响应缺少必填字段、连接断开：节点 ERROR，整次 200 + ERROR。 */
    @Test
    void test_nodeError() {
        channel.respond("POST", ORDERS, 500, "{\"message\":\"internal\"}")
            .respond("POST", ORDERS, 201, "{\"state\":\"CAPTURED\"}")
            .reply("POST", ORDERS, ChannelMocker.Reply.disconnected());

        for (int attempt = 0; attempt < 3; attempt++) {
            allowAccount(DEMO);
            assertResult(ExecuteStatus.ERROR, Map.of(), execute());
        }
        assertEquals(3, channel.requests().size());
    }

    /** 渠道响应超过原子 API 的响应超时：节点 ERROR。 */
    @Test
    void test_responseTimeout() {
        Integration slow =
            externalApi.integration("SLOW", mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
        var definition = ExternalApiSamples.createOrderDefinition();
        ((ObjectNode)definition.get("transport")).put("responseTimeoutMs", 100);
        externalApi.integrationApi(slow, ExternalApiSamples.CREATE_ORDER, definition);
        externalApi.implementation(slow, pay, ExternalApiSamples.payFlow());
        allowAccount(DEMO);
        channel.reply("POST", ORDERS,
            ChannelMocker.Reply.delayed(201, "{\"reference\":\"r\",\"state\":\"CAPTURED\"}", 1000));

        assertResult(ExecuteStatus.ERROR, Map.of(), execute("SLOW"));
    }

    /** Vault 不可用或账号配置与传入身份不一致：503 INTEGRATION_INITIALIZATION_FAILED，渠道没有收到请求。 */
    @Test
    void test_initializationFailed() {
        channelAccountVault.fail(TOKEN, 503);
        execute(DEMO).assertProblem(503, StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED);

        channelAccountVault.clear();
        allowAccount("OTHER");
        execute(DEMO).assertProblem(503, StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED);
        assertTrue(channel.requests().isEmpty());
    }

    private PlatformResponseChecker execute(String integrationCode) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + PAY + "/execute",
            new ExecuteStandardApiRequestBody(integrationCode, ExternalApiSamples.object("""
                {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}}""".formatted(ATTEMPT,
                TOKEN))));
    }

    private ExecuteStandardApiResponseBody execute() {
        return execute(DEMO).assertOk().as(ExecuteStandardApiResponseBody.class);
    }

    /** 登记合成账号配置：渠道编码按参数，门店改为带空格的值以验证路径段编码；账号与经营主体为本次 attemptId。 */
    private void allowAccount(String channelCode) {
        var account = ExternalApiFixtures.channelAccount().put("channelCode", channelCode);
        ((ObjectNode)account.get("parameters")).put("outletId", OUTLET);
        channelAccountVault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    private static void assertResult(ExecuteStatus status, Map<String, Object> outputs, Object response) {
        var body = response instanceof PlatformResponseChecker checker
            ? checker.assertOk().as(ExecuteStandardApiResponseBody.class) : (ExecuteStandardApiResponseBody)response;
        JsonNode actual = body.getOutputs();
        assertAll(() -> assertEquals(status.getCode(), body.getExecuteStatus()),
            () -> assertEquals(
                ExternalApiSamples.object(new tools.jackson.databind.json.JsonMapper().writeValueAsString(outputs)),
                actual));
    }

    private static String hmac(String key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
