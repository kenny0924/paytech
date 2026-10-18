package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ExecuteStatus;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
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
import com.paytech.platform.support.test.mocker.external.ChannelMocker;

import tools.jackson.databind.node.ObjectNode;

/**
 * 对最终字节签名的渠道协议（PayerMax 接入技术方案第 3 节）：REQUEST 脚本写入协议时间后以 sealJsonBody 锁定请求体并用
 * rsaSha256Sign 签名，渠道收到的字节即签名原文；decimalEncoding=NUMBER 使金额以 JSON 数字出入；RESPONSE 脚本对原始响应
 * requireRsaSha256Signature 验签，失败即节点 ERROR；锁定后再改请求体同样 ERROR 且不发出请求。
 *
 * <p>
 * 渠道是测试 Mock HTTP 服务：用商户公钥独立验证收到的签名，并用渠道私钥对配置的响应原文签名。密钥对每次运行随机生成，属合成数据。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiSignedChannelTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String CALL = "CALL";
    private static final String CODE = "SIGNED";
    private static final String PATH = "/api/orderAndPay";
    private static final String TOKEN = ChannelAccountVaultMocker.token("signed-account");
    private static final KeyPair MERCHANT = keyPair();
    private static final KeyPair CHANNEL = keyPair();
    /** 测试时钟 CredentialFixture.NOW 按 RFC 3339 毫秒精度。 */
    private static final String REQUEST_TIME = "2026-09-17T12:00:00.123Z";

    private ObjectNode account;

    /** 本类公共：金额为 DECIMAL 的标准 API PAY、RSA 签名与验签脚本、签名下单原子 API、流程与渠道账号（商户私钥、渠道公钥）。 */
    @BeforeEach
    void setUpClassMocks() {
        StandardApi pay = standardApi(PAY);
        account = ExternalApiFixtures.channelAccount();
        ((ObjectNode)account.get("parameters")).put("channelPublicKey", encode(CHANNEL.getPublic().getEncoded()));
        ((ObjectNode)account.get("credentials")).put("privateKey", encode(MERCHANT.getPrivate().getEncoded()));
        signedIntegration(pay, "rsa-sign");
    }

    /**
     * 成功：渠道收到的请求体按字段顺序编码、金额为 JSON 数字、协议时间取自应用时钟，sign 是该字节的 SHA256withRSA；
     * 响应验签通过，整数金额按 DECIMAL 输出；日志不含私钥。
     */
    @Test
    void test_signedRequestAndVerifiedResponse(CapturedOutput output) {
        respond(200, """
            {"code":"APPLY_SUCCESS","data":{"tradeToken":"T2026","totalAmount":10}}""", CHANNEL.getPrivate());

        var body = execute("10.50").assertOk().as(ExecuteStandardApiResponseBody.class);

        var request = channel.only();
        String expected = """
            {"version":"1.5","data":{"outTradeNo":"%s","totalAmount":10.50},"requestTime":"%s"}"""
            .formatted(attempt(), REQUEST_TIME);
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"PENDING","transactionRef":"T2026","amount":"10"}"""), body.getOutputs()),
            () -> assertEquals(expected, request.body()),
            () -> assertEquals("application/json", request.headers().get("content-type")),
            () -> assertTrue(verify(MERCHANT.getPublic(), request.body(), request.headers().get("sign")), "渠道验签"),
            () -> assertFalse(output.getAll().contains(credential("privateKey")), "日志包含私钥"));
    }

    /** 渠道明确拒绝且响应签名有效：FAIL，返回渠道错误码。 */
    @Test
    void test_rejectedWithSignedResponse() {
        respond(200, "{\"code\":\"PARAMS_INVALID\",\"msg\":\"outTradeNo is invalid\"}", CHANNEL.getPrivate());

        var body = execute("10.50").assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.FAIL.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"REJECTED","failureCode":"PARAMS_INVALID"}"""), body.getOutputs()));
    }

    /** 响应签名来自其他私钥：验签失败，即使响应声明受理也是 ERROR。 */
    @Test
    void test_responseSignedByOtherKey() {
        respond(200, "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T1\",\"totalAmount\":10}}",
            MERCHANT.getPrivate());

        assertError(execute("10.50"));
        assertEquals(1, channel.requests().size());
    }

    /** 响应缺少 sign：ERROR。 */
    @Test
    void test_responseWithoutSignature() {
        channel.reply("POST", PATH, new ChannelMocker.Reply(200, Map.of("Content-Type", "application/json"),
            "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T1\",\"totalAmount\":10}}", 0, false));

        assertError(execute("10.50"));
    }

    /** 锁定后脚本再改请求体：节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_modifyBodyAfterSeal() {
        signedIntegration(standardApi("PAY_MODIFY"), "rsa-sign-then-modify", "MODIFY");

        assertError(anonymous(HttpMethod.POST, "/standard-apis/v1/PAY_MODIFY/execute", requestBody("MODIFY", "1.00")));
        assertTrue(channel.requests().isEmpty());
    }

    /** 金额为 DECIMAL 的支付标准 API。 */
    private StandardApi standardApi(String code) {
        return externalApi.standardApi(code, List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
                field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
                field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
                field("paymentContext.vaultToken", ApiFieldDataType.STRING, true)),
            List.of(field("paymentStatus", ApiFieldDataType.STRING, true),
                field("transactionRef", ApiFieldDataType.STRING, false), field("amount", ApiFieldDataType.DECIMAL, false),
                field("failureCode", ApiFieldDataType.STRING, false)));
    }

    /** 以签名下单原子 API、指定的签名脚本与验签脚本实现标准 API。 */
    private void signedIntegration(StandardApi standardApi, String signScript) {
        signedIntegration(standardApi, signScript, CODE);
    }

    private void signedIntegration(StandardApi standardApi, String signScript, String code) {
        Integration integration =
            externalApi.integration(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
        externalApi.script(integration, "RSA_SIGN", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("privateKey", "PROTECTED_HANDLE", true)),
            ExternalApiFixtures.script(signScript));
        externalApi.script(integration, "RSA_VERIFY", ScriptExecutionStage.RESPONSE,
            List.of(new ParameterDeclaration("publicKey", "STRING", true)), ExternalApiFixtures.script("rsa-verify"));
        externalApi.integrationApi(integration, CALL, ExternalApiFixtures.definition("signed-create-order"),
            List.of(field("version", ApiFieldDataType.STRING, true), field("requestTime", ApiFieldDataType.STRING, false),
                field("data", ApiFieldDataType.OBJECT, false), field("data.outTradeNo", ApiFieldDataType.STRING, true),
                field("data.totalAmount", ApiFieldDataType.DECIMAL, true)),
            List.of(field("code", ApiFieldDataType.STRING, true), field("data", ApiFieldDataType.OBJECT, false),
                field("data.tradeToken", ApiFieldDataType.STRING, false),
                field("data.totalAmount", ApiFieldDataType.DECIMAL, false)));
        externalApi.implementation(integration, standardApi, ExternalApiFixtures.flow("call-signed-create-order"));
        channelAccountVault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** 渠道以给定私钥对响应原文签名后返回。 */
    private void respond(int status, String json, PrivateKey key) {
        channel.reply("POST", PATH, new ChannelMocker.Reply(status,
            Map.of("Content-Type", "application/json", "sign", sign(key, json)), json, 0, false));
    }

    private PlatformResponseChecker execute(String amount) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + PAY + "/execute", requestBody(CODE, amount));
    }

    private ExecuteStandardApiRequestBody requestBody(String integrationCode, String amount) {
        return new ExecuteStandardApiRequestBody(integrationCode, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"%s","vaultToken":"%s"}}""".formatted(attempt(), amount,
            TOKEN)));
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    private String credential(String name) {
        return account.path("credentials").path(name).asString();
    }

    private static void assertError(PlatformResponseChecker checker) {
        var body = checker.assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.ERROR.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("{}"), body.getOutputs()));
    }

    private static ApiFieldBody field(String path, ApiFieldDataType type, boolean required) {
        return ExternalApiSamples.field(ApiFieldLocation.BODY, path, type, required);
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String sign(PrivateKey key, String text) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(text.getBytes(StandardCharsets.UTF_8));
            return encode(signer.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean verify(PublicKey key, String text, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(text.getBytes(StandardCharsets.UTF_8));
            return signature != null && verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
