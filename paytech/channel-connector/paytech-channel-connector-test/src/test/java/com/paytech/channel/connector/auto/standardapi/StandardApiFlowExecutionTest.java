package com.paytech.channel.connector.auto.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Integration;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.ChannelMocker;

import tools.jackson.databind.node.ObjectNode;

/**
 * 标准 API 完整执行：初始化项经 Vault 读取渠道账号，下单节点组装请求、REQUEST 脚本以受控句柄签名、 发送到本机 Mock 渠道，按状态规则得到 SUCCESS / FAIL / ERROR 并映射出口。
 *
 * <p>
 * 第一个节点开始后的渠道错误、断连与超时都是 ERROR 结果；初始化失败是未发出（INTEGRATION_INITIALIZATION_FAILED），且渠道没有收到请求。
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
class StandardApiFlowExecutionTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String DEMO = "DEMO";
    private static final String OUTLET = "outlet 1";
    private static final String ATTEMPT = ExternalApiFixtures.channelAccount().path("channelAccountId").asString();
    private static final String API_KEY =
        ExternalApiFixtures.channelAccount().path("credentials").path("apiKey").asString();
    private static final String ORDERS = "/api/outlets/outlet%201/orders";
    private static final String TOKEN = VaultMocker.token("demo-account");

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
            () -> assertEquals(1, vault.revealCount()));
    }

    /** 渠道 201 DECLINED：FAIL，走拒绝出口。 */
    @Test
    void test_declined() {
        allowAccount(DEMO);
        channel.respond("POST", ORDERS, 201, "{\"state\":\"DECLINED\",\"errorCode\":\"E51\"}");

        assertResult(ExecuteStatus.FAIL, Map.of("paymentStatus", "DECLINED"), execute());
    }

    /** 状态规则全部为假（渠道 500）、成功响应缺少必填字段、连接断开：节点 ERROR，整次结果 ERROR。 */
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

    /** Vault 不可用或账号配置与传入身份不一致：未发出 INTEGRATION_INITIALIZATION_FAILED，渠道没有收到请求。 */
    @Test
    void test_initializationFailed() {
        vault.fail(TOKEN, 503);
        assertNotSent(StandardApiNotSentCode.INTEGRATION_INITIALIZATION_FAILED, () -> execute(DEMO));

        vault.clear();
        allowAccount("OTHER");
        assertNotSent(StandardApiNotSentCode.INTEGRATION_INITIALIZATION_FAILED, () -> execute(DEMO));
        assertTrue(channel.requests().isEmpty());
    }

    /** 以合成支付上下文经进程内入口执行 PAY。 */
    private ExecutionResult execute(String integrationCode) {
        return execute(PAY, integrationCode, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s"}}""".formatted(ATTEMPT, TOKEN)));
    }

    /** 经默认接入方 DEMO 执行。 */
    private ExecutionResult execute() {
        return execute(DEMO);
    }

    /** 登记合成账号配置：渠道编码按参数，门店改为带空格的值以验证路径段编码；账号与经营主体为本次 attemptId。 */
    private void allowAccount(String channelCode) {
        var account = ExternalApiFixtures.channelAccount().put("channelCode", channelCode);
        ((ObjectNode)account.get("parameters")).put("outletId", OUTLET);
        vault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** 执行状态与输出（十进制写为字符串的 JSON 口径）。 */
    private static void assertResult(ExecuteStatus status, Map<String, Object> outputs, ExecutionResult result) {
        assertAll(() -> assertEquals(status, result.getExecuteStatus()),
            () -> assertEquals(
                ExternalApiSamples.object(new tools.jackson.databind.json.JsonMapper().writeValueAsString(outputs)),
                outputs(result)));
    }

    /** HMAC-SHA256 十六进制签名。 */
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
