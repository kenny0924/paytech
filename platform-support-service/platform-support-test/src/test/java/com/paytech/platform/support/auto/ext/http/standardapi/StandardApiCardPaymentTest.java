package com.paytech.platform.support.auto.ext.http.standardapi;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.enums.ExecuteStatus;
import com.paytech.platform.support.api.enums.ScriptExecutionStage;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.enums.VerifyRunStatus;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyAccountRequestBody;
import com.paytech.platform.support.api.externalapi.request.CreateVerifyCaseRequestBody;
import com.paytech.platform.support.api.externalapi.request.StartVerifyRunRequestBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunResponseBody;
import com.paytech.platform.support.api.externalapi.response.VerifyRunStartResponseBody;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;
import com.paytech.platform.support.integration.core.dal.dataobject.Integration;
import com.paytech.platform.support.integration.core.dal.dataobject.StandardApi;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domainservice.function.PaymentCardFunction;
import com.paytech.platform.support.integration.domainservice.function.VerifyPaymentCardFunction;
import com.paytech.platform.support.test.base.ExternalApiTestBase;
import com.paytech.platform.support.test.builder.ExternalApiFixtures;
import com.paytech.platform.support.test.builder.ExternalApiSamples;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.VerifyTestMapper;
import com.paytech.platform.support.test.mock.external.ChannelMockController;
import com.paytech.platform.support.test.mocker.external.ChannelAccountVaultMocker;
import com.paytech.platform.support.test.mocker.external.PaymentCardMocker;
import com.paytech.platform.support.test.mocker.local.VerifyRunFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 卡直付执行（PayerMax 卡直付对接技术方案第 2、3 节）：流程初始化项 PAYMENT_CARD 凭支付工具 Token 向 Payment 读卡，卡值只写入原子 API
 * 声明的卡字段，在 sealJsonBody 生成的发送字节中展开并被签名覆盖；响应、日志不含卡值。读卡失败或卡数据格式无效为 503 且不发出渠道请求。
 * 沙箱验证运行改读验证账号测试卡、不调用 Payment，证据中的请求体卡号只留前 6 后 4，其余卡值为 [CARD]。
 *
 * <p>
 * Payment 与渠道都是测试 Mock HTTP 服务；卡号为公开测试卡号，密钥每次运行随机生成，均为合成数据。
 *
 * @see StandardApiCommandApi#executeStandardApi(String, ExecuteStandardApiRequestBody)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiCardPaymentTest extends ExternalApiTestBase {

    private static final String PAY = "CARD_PAY";
    private static final String CALL = "CALL";
    private static final String CODE = "CARD";
    private static final String SANDBOX = "CARD_SANDBOX";
    private static final String PATH = "/api/orderAndPay";
    private static final String VAULT_TOKEN = ChannelAccountVaultMocker.token("card-account");
    private static final String INSTRUMENT = "pit_card_001";
    private static final String PAN = "4111111111111111";
    private static final String CVV = "737";
    private static final String HOLDER = "TEST CARDHOLDER";
    private static final KeyPair MERCHANT = keyPair();

    @Autowired
    private VerifyRunFixture runs;

    @Autowired
    private VerifyTestMapper verifyMapper;

    private ObjectNode account;
    private StandardApi pay;

    /** 本类公共：卡支付标准 API、签名卡下单原子 API 与读卡流程、渠道账号（商户私钥）。 */
    @BeforeEach
    void setUpClassMocks() {
        pay = externalApi.standardApi(PAY, List.of(field("paymentContext", ApiFieldDataType.OBJECT, true),
                field("paymentContext.attemptId", ApiFieldDataType.STRING, true),
                field("paymentContext.amount", ApiFieldDataType.DECIMAL, true),
                field("paymentContext.vaultToken", ApiFieldDataType.STRING, true),
                field("paymentContext.instrumentToken", ApiFieldDataType.STRING, true)),
            List.of(field("paymentStatus", ApiFieldDataType.STRING, true),
                field("transactionRef", ApiFieldDataType.STRING, false)));
        account = ExternalApiFixtures.channelAccount();
        ((ObjectNode)account.get("credentials")).put("privateKey", encode(MERCHANT.getPrivate().getEncoded()));
        cardIntegration(CODE, false);
        channelAccountVault.allow(VAULT_TOKEN, ExternalApiSamples.text(account));
    }

    /**
     * 卡值只出现在渠道收到的字节中：卡号、两位年份、CVV 与持卡人写入卡字段，签名覆盖这些字节；读卡请求带调用方、Token、支付尝试与用途；
     * 标准 API 响应与日志不含卡号和 CVV。
     */
    @Test
    void test_cardOnlyInChannelBytes(CapturedOutput output) {
        paymentCard.allow(INSTRUMENT, PAN, "12", "2030", HOLDER, CVV);
        channel.respond("POST", PATH, 200, "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T-CARD\"}}");

        var body = execute(INSTRUMENT).assertOk().as(ExecuteStandardApiResponseBody.class);

        var request = channel.only();
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS.getCode(), body.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"SUCCEEDED","transactionRef":"T-CARD"}"""), body.getOutputs()),
            () -> assertTrue(request.body().contains("""
                "card":{"number":"%s","expMonth":"12","expYear":"30","cvv":"%s","holder":"%s"}""".formatted(PAN, CVV, HOLDER)),
                request.body()),
            () -> assertTrue(verify(MERCHANT.getPublic(), request.body(), request.headers().get("sign")), "签名覆盖卡值"),
            () -> assertEquals(List.of(new PaymentCardMocker.Reveal(PaymentCardFunction.CALLER, INSTRUMENT, attempt(),
                PaymentCardFunction.PURPOSE)), paymentCard.reveals()),
            () -> assertFalse(output.getAll().contains(PAN), "日志包含卡号"),
            () -> assertFalse(output.getAll().contains("\"cvv\":\"" + CVV + "\""), "日志包含 CVV"));
    }

    /** Payment 拒绝读卡（例如 CVV 已领取）：503 INTEGRATION_INITIALIZATION_FAILED，渠道没有收到请求。 */
    @Test
    void test_revealRejectedWithoutChannelRequest() {
        paymentCard.fail(INSTRUMENT, 409);

        execute(INSTRUMENT).assertProblem(503, StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED);

        assertAll(() -> assertTrue(channel.requests().isEmpty()), () -> assertEquals(1, paymentCard.reveals().size()));
    }

    /** Payment 返回的卡数据格式无效：同样 503 且不发出渠道请求，不回显卡数据。 */
    @Test
    void test_invalidCardWithoutChannelRequest() {
        paymentCard.allow(INSTRUMENT, "4111", "12", "2030", HOLDER, CVV);

        execute(INSTRUMENT).assertProblem(503, StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED);

        assertTrue(channel.requests().isEmpty());
    }

    /** 沙箱验证：测试卡来自验证账号、不调用 Payment；渠道收到原值，证据与库中轨迹只有遮蔽文本。 */
    @Test
    void test_verifyRunMasksCardEvidence() {
        Operator admin = administrator();
        cardIntegration(SANDBOX, true);
        var credentials = new LinkedHashMap<String, String>();
        credentials.put("privateKey", account.path("credentials").path("privateKey").asString());
        credentials.put(VerifyPaymentCardFunction.PREFIX + "pan", PAN);
        credentials.put(VerifyPaymentCardFunction.PREFIX + "expiryMonth", "12");
        credentials.put(VerifyPaymentCardFunction.PREFIX + "expiryYear", "2030");
        credentials.put(VerifyPaymentCardFunction.PREFIX + "cardholderName", HOLDER);
        credentials.put(VerifyPaymentCardFunction.PREFIX + "cvv", CVV);
        post(BASE + "/integrations/" + SANDBOX + "/verify-accounts", admin, new CreateVerifyAccountRequestBody("MAIN",
            "测试卡账号", "DEMO", attempt(), attempt(), Map.of(), credentials, "卡直付验证")).assertOk();
        post(BASE + "/integrations/" + SANDBOX + "/verify-cases", admin, new CreateVerifyCaseRequestBody("CARD_PAY",
            "卡支付", null, "MAIN", ExternalApiSamples.object("""
                {"steps":[{"id":"pay","standardApiCode":"%s",
                  "input":{"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s","instrumentToken":"unused"}},
                  "expect":{"executeStatus":"SUCCESS"}}]}""".formatted(PAY, attempt(), VAULT_TOKEN)).get("steps"),
            "卡直付验证")).assertOk();
        channel.respond("POST", PATH, 200, "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T-VERIFY\"}}");

        var started = post(BASE + "/integrations/" + SANDBOX + "/verify-cases/CARD_PAY/runs", admin,
            new StartVerifyRunRequestBody("卡直付验证")).assertOk().as(VerifyRunStartResponseBody.class);
        runs.awaitIdle();
        await().atMost(Duration.ofSeconds(5)).until(() -> !VerifyRunStatus.RUNNING.getCode()
            .equals(runDetail(admin, started.getRunCode()).getRunStatus()));
        VerifyRunResponseBody run = runDetail(admin, started.getRunCode());

        JsonNode sent = run.getSteps().getFirst().getTrace().path("nodes").get(0).path("exchange").path("request");
        String evidence = sent.path("body").path("text").asString();
        assertAll(() -> assertEquals(VerifyRunStatus.PASSED.getCode(), run.getRunStatus()),
            () -> assertTrue(evidence.contains("""
                "card":{"number":"411111******1111","expMonth":"[CARD]","expYear":"[CARD]","cvv":"[CARD]","holder":"[CARD]"}"""),
                evidence),
            () -> assertTrue(channel.only().body().contains(PAN)), () -> assertTrue(paymentCard.reveals().isEmpty()),
            () -> assertFalse(verifyMapper.traces(Long.parseLong(started.getRunCode())).stream()
                .anyMatch(trace -> trace != null && (trace.contains(PAN) || trace.contains("\"cvv\":\"" + CVV + "\""))),
                "轨迹包含卡值"));
    }

    /** 接入方：签名脚本、卡下单原子 API（五个卡字段）与读卡流程。 */
    private void cardIntegration(String code, boolean sandbox) {
        Integration integration =
            externalApi.integration(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api", sandbox);
        externalApi.script(integration, "RSA_SIGN", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("privateKey", "PROTECTED_HANDLE", true)), ExternalApiFixtures.script("rsa-sign"));
        externalApi.integrationApi(integration, CALL, ExternalApiFixtures.definition("card-create-order"),
            List.of(field("version", ApiFieldDataType.STRING, true), field("requestTime", ApiFieldDataType.STRING, false),
                field("data", ApiFieldDataType.OBJECT, false), field("data.outTradeNo", ApiFieldDataType.STRING, true),
                field("data.totalAmount", ApiFieldDataType.DECIMAL, true), field("data.card", ApiFieldDataType.OBJECT, false),
                field("data.card.number", ApiFieldDataType.STRING, true),
                field("data.card.expMonth", ApiFieldDataType.STRING, true),
                field("data.card.expYear", ApiFieldDataType.STRING, true), field("data.card.cvv", ApiFieldDataType.STRING, true),
                field("data.card.holder", ApiFieldDataType.STRING, true)),
            List.of(field("code", ApiFieldDataType.STRING, true), field("data", ApiFieldDataType.OBJECT, false),
                field("data.tradeToken", ApiFieldDataType.STRING, false)));
        externalApi.implementation(integration, pay, ExternalApiFixtures.flow("call-card-create-order"));
    }

    private PlatformResponseChecker execute(String instrumentToken) {
        return anonymous(HttpMethod.POST, "/standard-apis/v1/" + PAY + "/execute",
            new ExecuteStandardApiRequestBody(CODE, ExternalApiSamples.object("""
                {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s","instrumentToken":"%s"}}"""
                .formatted(attempt(), VAULT_TOKEN, instrumentToken))));
    }

    private VerifyRunResponseBody runDetail(Operator admin, String runCode) {
        return get(BASE + "/integrations/" + SANDBOX + "/verify-runs/" + runCode, admin).assertOk()
            .as(VerifyRunResponseBody.class);
    }

    /** 样例流程以 attemptId 作为渠道账号与经营主体，与账号配置一致。 */
    private String attempt() {
        return account.path("channelAccountId").asString();
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
