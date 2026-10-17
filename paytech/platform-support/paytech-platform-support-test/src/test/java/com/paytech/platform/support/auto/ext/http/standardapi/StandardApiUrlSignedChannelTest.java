package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * 签名值经 URL 编码放入 Header 的渠道协议（Alipay+ 接入技术方案 T1）：REQUEST 脚本以
 * {@code POST 路径\n客户号.时间.锁定字节} 签名后用 urlEncode 写入 Signature 头；RESPONSE 脚本从响应头取签名，
 * urlDecode 后按同样格式验签。金额以最小单位字符串出入：请求用 MONEY_MINOR_UNITS，原子 API 输出用 MONEY_MAJOR_UNITS。
 *
 * <p>
 * 渠道是测试 Mock HTTP 服务：用商户公钥独立验证收到的签名，并用渠道私钥对配置的响应签名。密钥对每次运行随机生成，属合成数据。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
class StandardApiUrlSignedChannelTest extends ExternalApiTestBase {

    private static final String PAY = "PAY";
    private static final String CODE = "URL_SIGNED";
    private static final String MOCK_PATH = "/aps/payments/pay";
    /** 签名原文中的路径，与原子 API 定义绑定的脚本参数一致。 */
    private static final String SIGNED_PATH = "/aps/api/v1/payments/pay";
    private static final String CLIENT_ID = "SANDBOX_TEST_CLIENT";
    private static final String TOKEN = ChannelAccountVaultMocker.token("url-signed-account");
    private static final KeyPair MERCHANT = keyPair();
    private static final KeyPair CHANNEL = keyPair();
    /** 测试时钟 CredentialFixture.NOW 按 RFC 3339 毫秒精度。 */
    private static final String REQUEST_TIME = "2026-09-17T12:00:00.123Z";
    private static final String RESPONSE_TIME = "2026-09-17T12:00:01Z";

    private ObjectNode account;

    /** 本类公共：标准 API PAY、URL 编码签名与验签脚本、下单原子 API、流程与渠道账号（客户号、商户私钥、渠道公钥）。 */
    @BeforeEach
    void setUpClassMocks() {
        StandardApi pay = externalApi.standardApi(PAY, List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
                field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
                field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
                field("paymentContext.currency", ApiFieldDataType.STRING, true),
                field("paymentContext.vaultToken", ApiFieldDataType.STRING, true)),
            List.of(field("paymentStatus", ApiFieldDataType.STRING, true),
                field("transactionRef", ApiFieldDataType.STRING, false), field("amount", ApiFieldDataType.DECIMAL, false),
                field("failureCode", ApiFieldDataType.STRING, false)));
        account = ExternalApiFixtures.channelAccount();
        ((ObjectNode)account.get("parameters")).put("clientId", CLIENT_ID)
            .put("channelPublicKey", encode(CHANNEL.getPublic().getEncoded()));
        ((ObjectNode)account.get("credentials")).put("privateKey", encode(MERCHANT.getPrivate().getEncoded()));
        integration(pay);
    }

    /**
     * 成功：Signature 头的签名值是 URL 编码的 Base64，解码后是对 POST 路径、客户号、协议时间与发送字节的签名；
     * 请求金额为最小单位字符串，响应验签通过，最小单位金额按币种精度输出为主单位。
     */
    @Test
    void test_signedRequestAndVerifiedResponse() {
        respond("""
            {"result":{"resultStatus":"U","resultCode":"PAYMENT_IN_PROCESS"},"paymentId":"P2026",\
            "paymentAmount":{"currency":"USD","value":"1050"}}""", CHANNEL.getPrivate());

        var body = execute("10.50").assertOk().as(ExecuteStandardApiResponseBody.class);

        var request = channel.only();
        String header = request.headers().get("signature");
        String encoded = header.substring(header.indexOf("signature=") + "signature=".length());
        String signed = "POST " + SIGNED_PATH + "\n" + CLIENT_ID + "." + REQUEST_TIME + "." + request.body();
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"PENDING","transactionRef":"P2026","amount":"10.50"}"""), body.getOutputs()),
            () -> assertEquals("""
                {"paymentRequestId":"%s","paymentAmount":{"currency":"USD","value":"1050"}}""".formatted(attempt()),
                request.body()),
            () -> assertEquals(CLIENT_ID, request.headers().get("client-id")),
            () -> assertEquals(REQUEST_TIME, request.headers().get("request-time")),
            () -> assertTrue(header.startsWith("algorithm=RSA256, keyVersion=0, signature=")),
            () -> assertTrue(encoded.matches("[A-Za-z0-9%]+"), "签名值未 URL 编码"),
            () -> assertTrue(verify(MERCHANT.getPublic(), signed, urlDecode(encoded)), "渠道验签"));
    }

    /** 渠道明确拒绝且响应签名有效：FAIL，返回渠道结果码。 */
    @Test
    void test_rejectedWithSignedResponse() {
        respond("{\"result\":{\"resultStatus\":\"F\",\"resultCode\":\"PARAM_ILLEGAL\"}}", CHANNEL.getPrivate());

        var body = execute("10.50").assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.FAIL.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"REJECTED","failureCode":"PARAM_ILLEGAL"}"""), body.getOutputs()));
    }

    /** 响应签名来自其他私钥、响应缺少签名或签名值的百分号序列非法：都是 ERROR，未签名的 F 不当作确定失败。 */
    @Test
    void test_responseNotVerified() {
        String rejected = "{\"result\":{\"resultStatus\":\"F\",\"resultCode\":\"PARAM_ILLEGAL\"}}";
        respond(rejected, MERCHANT.getPrivate());
        assertError(execute("10.50"));

        reply(rejected, Map.of());
        assertError(execute("10.50"));

        reply(rejected, Map.of("signature", "algorithm=RSA256,keyVersion=0,signature=%G0"));
        assertError(execute("10.50"));
        assertEquals(3, channel.requests().size());
    }

    /** 金额超出币种精度：MONEY_MINOR_UNITS 失败，节点 ERROR，渠道没有收到请求。 */
    @Test
    void test_amountBeyondPrecision() {
        assertError(execute("10.505"));
        assertTrue(channel.requests().isEmpty());
    }

    /** 以 URL 编码签名的下单原子 API 实现标准 API。 */
    private void integration(StandardApi standardApi) {
        Integration integration =
            externalApi.integration(CODE, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/aps");
        externalApi.script(integration, "URL_SIGN", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("privateKey", "PROTECTED_HANDLE", true),
                new ParameterDeclaration("clientId", "STRING", true), new ParameterDeclaration("path", "STRING", true)),
            ExternalApiFixtures.script("url-sign"));
        externalApi.script(integration, "URL_VERIFY", ScriptExecutionStage.RESPONSE,
            List.of(new ParameterDeclaration("publicKey", "STRING", true), new ParameterDeclaration("path", "STRING", true)),
            ExternalApiFixtures.script("url-verify"));
        externalApi.integrationApi(integration, "CALL", ExternalApiFixtures.definition("url-signed-create-order"),
            List.of(field("paymentRequestId", ApiFieldDataType.STRING, true),
                field("paymentAmount", ApiFieldDataType.OBJECT, false),
                field("paymentAmount.currency", ApiFieldDataType.STRING, true),
                field("paymentAmount.value", ApiFieldDataType.STRING, true)),
            List.of(field("result", ApiFieldDataType.OBJECT, true), field("result.resultStatus", ApiFieldDataType.STRING, true),
                field("result.resultCode", ApiFieldDataType.STRING, true), field("paymentId", ApiFieldDataType.STRING, false),
                field("paymentAmount", ApiFieldDataType.OBJECT, false),
                field("paymentAmount.currency", ApiFieldDataType.STRING, false),
                field("paymentAmount.value", ApiFieldDataType.STRING, false)));
        externalApi.implementation(integration, standardApi, ExternalApiFixtures.flow("call-url-signed-create-order"));
        channelAccountVault.allow(TOKEN, ExternalApiSamples.text(account));
    }

    /** 渠道以给定私钥按 POST 路径、客户号、响应时间与响应原文签名，签名值 URL 编码后放入 signature 头。 */
    private void respond(String json, PrivateKey key) {
        String signed = "POST " + SIGNED_PATH + "\n" + CLIENT_ID + "." + RESPONSE_TIME + "." + json;
        reply(json, Map.of("signature",
            "algorithm=RSA256,keyVersion=0,signature=" + URLEncoder.encode(sign(key, signed), StandardCharsets.UTF_8)));
    }

    private void reply(String json, Map<String, String> signature) {
        var headers = new HashMap<>(signature);
        headers.putAll(Map.of("Content-Type", "application/json", "client-id", CLIENT_ID, "response-time", RESPONSE_TIME));
        channel.reply("POST", MOCK_PATH, new ChannelMocker.Reply(200, headers, json, 0, false));
    }

    private PlatformResponseChecker execute(String amount) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + PAY + "/execute",
            new ExecuteStandardApiRequestBody(CODE, ExternalApiSamples.object("""
                {"paymentContext":{"attemptId":"%s","amount":"%s","currency":"USD","vaultToken":"%s"}}"""
                .formatted(attempt(), amount, TOKEN))));
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
    }

    private static void assertError(PlatformResponseChecker checker) {
        var body = checker.assertOk().as(ExecuteStandardApiResponseBody.class);
        assertAll(() -> assertEquals(ExecuteStatus.ERROR.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("{}"), body.getOutputs()));
    }

    private static ApiFieldBody field(String path, ApiFieldDataType type, boolean required) {
        return ExternalApiSamples.field(ApiFieldLocation.BODY, path, type, required);
    }

    /** 渠道侧解码：Base64 字符表中只有 + / = 需要百分号编码。 */
    private static String urlDecode(String value) {
        return value.replace("%2B", "+").replace("%2F", "/").replace("%3D", "=");
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
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
