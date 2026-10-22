package com.paytech.channel.connector.auto.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.TestClock;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.ChannelMocker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 对最终字节签名的渠道协议（PayerMax 接入技术方案第 3 节）：REQUEST 脚本写入协议时间后以 sealJsonBody 锁定请求体并用
 * rsaSha256Sign 签名，渠道收到的字节即签名原文；decimalEncoding=NUMBER 使金额以 JSON 数字出入；RESPONSE 脚本对原始响应
 * requireRsaSha256Signature 验签，失败即节点 ERROR；锁定后再改请求体同样 ERROR 且不发出请求。
 *
 * <p>
 * Cybersource 式 HTTP Signature（CYBS_NI 接入技术方案第 4 节）：REQUEST 脚本对锁定字节求 sha256Base64 作 Digest，以 nowRfc1123
 * 写 Date，用 hmacSha256Base64DecodedKey 对 host、date、request-target、digest、v-c-merchant-id 五行签名。
 *
 * <p>
 * 渠道是测试 Mock HTTP 服务：用商户公钥独立验证收到的签名，并用渠道私钥对配置的响应原文签名。密钥对每次运行随机生成，属合成数据；
 * HMAC 密钥是对接包 test-vectors.json 中本机随机生成的测试专用值（testOnly），与任何真实商户无关。
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiSignedChannelTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String CALL = "CALL";
    private static final String CODE = "SIGNED";
    private static final String PATH = "/api/orderAndPay";
    private static final String TOKEN = VaultMocker.token("signed-account");
    private static final KeyPair MERCHANT = keyPair();
    private static final KeyPair CHANNEL = keyPair();
    /** 固定测试时钟 TestClock.PLATFORM_FIXED_NOW 按 RFC 3339 毫秒精度。 */
    private static final String REQUEST_TIME = "2026-09-17T12:00:00.123Z";
    /** 同一固定时钟按 RFC 1123 秒精度。 */
    private static final String HTTP_DATE = "Thu, 17 Sep 2026 12:00:00 GMT";
    private static final String CYBS = "CYBS";
    private static final String CYBS_PAY = "PAY_CYBS";
    /** 签名串中的 request-target，与原子 API 定义绑定的脚本参数一致。 */
    private static final String CYBS_REQUEST_TARGET = "post /pts/v2/payments";
    private static final String CYBS_HOST = "apitest.cybersource.com";
    private static final String CYBS_MERCHANT_ID = "testonly_merchant";
    private static final String CYBS_KEY_ID = "testonly-key-id";
    /** 测试专用 HMAC 密钥（Base64）。 */
    private static final String CYBS_SECRET_KEY = "v9EJBDh1IuYowEzGJ4Wr74fy/eFcxHs+tzby2ypVChc=";

    @Autowired
    private TestClock clock;

    private ObjectNode account;

    /** 本类公共：金额为 DECIMAL 的标准 API PAY、RSA 签名与验签脚本、签名下单原子 API、流程与渠道账号（商户私钥、渠道公钥）。 */
    @BeforeEach
    void setUpClassMocks() {
        clock.fix(TestClock.PLATFORM_FIXED_NOW);
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

        var result = execute("10.50");

        var request = channel.only();
        String expected = """
            {"version":"1.5","data":{"outTradeNo":"%s","totalAmount":10.50},"requestTime":"%s"}"""
            .formatted(attempt(), REQUEST_TIME);
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"PENDING","transactionRef":"T2026","amount":"10"}"""), outputs(result)),
            () -> assertEquals(expected, request.body()),
            () -> assertEquals("application/json", request.headers().get("content-type")),
            () -> assertTrue(verify(MERCHANT.getPublic(), request.body(), request.headers().get("sign")), "渠道验签"),
            () -> assertFalse(output.getAll().contains(credential("privateKey")), "日志包含私钥"));
    }

    /** 渠道明确拒绝且响应签名有效：FAIL，返回渠道错误码。 */
    @Test
    void test_rejectedWithSignedResponse() {
        respond(200, "{\"code\":\"PARAMS_INVALID\",\"msg\":\"outTradeNo is invalid\"}", CHANNEL.getPrivate());

        var result = execute("10.50");
        assertAll(() -> assertEquals(ExecuteStatus.FAIL, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"REJECTED","failureCode":"PARAMS_INVALID"}"""), outputs(result)));
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

        assertError(execute("PAY_MODIFY", "MODIFY", input("1.00")));
        assertTrue(channel.requests().isEmpty());
    }

    /**
     * Cybersource 式签名：渠道对实际收到的请求体字节独立重算 Digest，按收到的 Date 与账号参数重建五行签名串并以解码后的密钥重算
     * Signature；Date 取自应用时钟；日志与执行结果不含密钥。
     */
    @Test
    void test_cybersourceHttpSignature(CapturedOutput output) {
        cybersourceIntegration();
        channel.respond("POST", PATH, 200, """
            {"code":"APPLY_SUCCESS","data":{"tradeToken":"T2026","totalAmount":10}}""");

        var result = execute(CYBS_PAY, CYBS, input("10.50"));

        var request = channel.only();
        String digest = "SHA-256=" + encode(sha256(request.body().getBytes(StandardCharsets.UTF_8)));
        String content = String.join("\n", "host: " + CYBS_HOST, "date: " + request.headers().get("date"),
            "request-target: " + CYBS_REQUEST_TARGET, "digest: " + digest, "v-c-merchant-id: " + CYBS_MERCHANT_ID);
        String signature = """
            keyid="%s", algorithm="HmacSHA256", headers="host date request-target digest v-c-merchant-id", \
            signature="%s\"""".formatted(CYBS_KEY_ID, hmacSha256(CYBS_SECRET_KEY, content));
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, result.getExecuteStatus()),
            () -> assertEquals("""
                {"version":"1.5","data":{"outTradeNo":"%s","totalAmount":10.50}}""".formatted(attempt()), request.body()),
            () -> assertEquals(digest, request.headers().get("digest"), "Digest 未覆盖实际发送的字节"),
            () -> assertEquals(HTTP_DATE, request.headers().get("date")),
            () -> assertEquals(CYBS_MERCHANT_ID, request.headers().get("v-c-merchant-id")),
            () -> assertEquals(signature, request.headers().get("signature"), "渠道验签"),
            () -> assertFalse(outputs(result).toString().contains(CYBS_SECRET_KEY), "执行结果包含密钥"),
            () -> assertFalse(output.getAll().contains(CYBS_SECRET_KEY), "日志包含密钥"));
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

    /** 以签名下单原子 API、指定的签名脚本与验签脚本，在指定渠道编码下实现标准 API。 */
    private void signedIntegration(StandardApi standardApi, String signScript, String code) {
        Channel integration =
            externalApi.channel(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
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
        vault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** 以 Cybersource 式签名脚本与下单原子 API 实现标准 API；账号参数含 host、merchantId、keyId，凭据含 Base64 密钥。 */
    private void cybersourceIntegration() {
        Channel integration =
            externalApi.channel(CYBS, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api");
        externalApi.script(integration, "CYBS_SIGN", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("secretKey", "PROTECTED_HANDLE", true),
                new ParameterDeclaration("keyId", "STRING", true), new ParameterDeclaration("merchantId", "STRING", true),
                new ParameterDeclaration("host", "STRING", true), new ParameterDeclaration("requestTarget", "STRING", true)),
            ExternalApiFixtures.script("cybs-signature"));
        externalApi.integrationApi(integration, CALL, ExternalApiFixtures.definition("cybs-signed-create-order"),
            List.of(field("version", ApiFieldDataType.STRING, true), field("data", ApiFieldDataType.OBJECT, false),
                field("data.outTradeNo", ApiFieldDataType.STRING, true),
                field("data.totalAmount", ApiFieldDataType.DECIMAL, true)),
            List.of(field("code", ApiFieldDataType.STRING, true), field("data", ApiFieldDataType.OBJECT, false),
                field("data.tradeToken", ApiFieldDataType.STRING, false),
                field("data.totalAmount", ApiFieldDataType.DECIMAL, false)));
        externalApi.implementation(integration, standardApi(CYBS_PAY),
            ExternalApiFixtures.flow("call-cybs-signed-create-order"));
        ((ObjectNode)account.get("parameters")).put("host", CYBS_HOST).put("merchantId", CYBS_MERCHANT_ID)
            .put("keyId", CYBS_KEY_ID);
        ((ObjectNode)account.get("credentials")).put("secretKey", CYBS_SECRET_KEY);
        vault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** 渠道以给定私钥对响应原文签名后返回。 */
    private void respond(int status, String json, PrivateKey key) {
        channel.reply("POST", PATH, new ChannelMocker.Reply(status,
            Map.of("Content-Type", "application/json", "sign", sign(key, json)), json, 0, false));
    }

    /** 以指定金额经进程内入口执行默认渠道的标准 API。 */
    private ExecutionResult execute(String amount) {
        return execute(PAY, CODE, input(amount));
    }

    /** 指定金额的合成支付上下文。 */
    private JsonNode input(String amount) {
        return ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"%s","vaultToken":"%s"}}""".formatted(attempt(), amount,
            TOKEN));
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    /** 账号配置中的合成凭据。 */
    private String credential(String name) {
        return account.path("credentials").path(name).asString();
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

    /** 随机生成的合成 RSA 密钥对。 */
    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** SHA256withRSA 签名，Base64 编码。 */
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

    /** SHA256withRSA 验签。 */
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

    /** SHA-256 摘要。 */
    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 以 Base64 解码后的密钥计算 HmacSHA256，Base64 编码。 */
    private static String hmacSha256(String base64Key, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(base64Key), "HmacSHA256"));
            return encode(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 标准 Base64 编码。 */
    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
