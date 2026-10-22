package com.paytech.channel.connector.auto.standardapi;

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
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.card.api.CardApiConstants;
import com.paytech.channel.connector.protocol.domainservice.function.VerifyPaymentCardFunction;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.test.base.ExternalApiTestBase;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.dal.VerifyTestMapper;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.VaultMocker;
import com.paytech.channel.connector.test.mocker.PaymentCardMocker;
import com.paytech.channel.connector.test.mocker.VerifyRunFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 卡直付执行（PayerMax 卡直付对接技术方案第 2、3 节）：流程初始化项 PAYMENT_CARD 以 channel-connector-service 身份凭一次性读卡票据
 * 向 card 读卡，卡值只写入原子 API 声明的卡字段，在 sealJsonBody 生成的发送字节中展开并被签名覆盖；执行结果、日志不含卡值。
 * 读卡失败或卡数据格式无效为未发出（INTEGRATION_INITIALIZATION_FAILED）且不发出渠道请求。
 * 沙箱验证运行改读验证账号测试卡、不调用 card，证据中的请求体卡号只留前 6 后 4，其余卡值为 [CARD]。
 *
 * <p>
 * card 与渠道都是测试 Mock HTTP 服务；卡号为公开测试卡号，密钥每次运行随机生成，均为合成数据。
 *
 * @see ExecuteStandardApiProcessor#process(ExecuteStandardApiRequest)
 */
@ExtendWith(OutputCaptureExtension.class)
class StandardApiCardPaymentTest extends ExternalApiTestBase {

    private static final String PAY = "CARD_PAY";
    private static final String CALL = "CALL";
    private static final String CODE = "CARD";
    private static final String SANDBOX = "CARD_SANDBOX";
    private static final String PATH = "/api/orderAndPay";
    private static final String VAULT_TOKEN = VaultMocker.token("card-account");
    private static final String INSTRUMENT = "tkt_card_0000000000000000000000000000000001";
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
        vault.allow(VAULT_TOKEN, ExternalApiSamples.text(account));
    }

    /**
     * 卡值只出现在渠道收到的字节中：卡号、两位年份、CVV 与持卡人写入卡字段，签名覆盖这些字节；读卡请求带调用方
     * channel-connector-service 与票据；标准 API 执行结果与日志不含卡号和 CVV。
     */
    @Test
    void test_cardOnlyInChannelBytes(CapturedOutput output) {
        paymentCard.allow(INSTRUMENT, PAN, "12", "2030", HOLDER, CVV);
        channel.respond("POST", PATH, 200, "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T-CARD\"}}");

        var result = execute(INSTRUMENT);

        var request = channel.only();
        assertAll(() -> assertEquals(ExecuteStatus.SUCCESS, result.getExecuteStatus()),
            () -> assertEquals(ExternalApiSamples.object("""
                {"paymentStatus":"SUCCEEDED","transactionRef":"T-CARD"}"""), outputs(result)),
            () -> assertFalse(outputs(result).toString().contains(PAN), "执行结果包含卡号"),
            () -> assertTrue(request.body().contains("""
                "card":{"number":"%s","expMonth":"12","expYear":"30","cvv":"%s","holder":"%s"}""".formatted(PAN, CVV, HOLDER)),
                request.body()),
            () -> assertTrue(verify(MERCHANT.getPublic(), request.body(), request.headers().get("sign")), "签名覆盖卡值"),
            () -> assertEquals(List.of(new PaymentCardMocker.Reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, INSTRUMENT)),
                paymentCard.reveals()),
            () -> assertEquals(List.of(PaymentCardMocker.NO_AUTHORIZATION), paymentCard.authorizations(),
                "internal-auth.mode=OFF 时出站不附加内部 Token"),
            () -> assertFalse(output.getAll().contains(PAN), "日志包含卡号"),
            () -> assertFalse(output.getAll().contains("\"cvv\":\"" + CVV + "\""), "日志包含 CVV"));
    }

    /** card 拒绝读卡（例如 CVV 已领取）：未发出 INTEGRATION_INITIALIZATION_FAILED，渠道没有收到请求。 */
    @Test
    void test_revealRejectedWithoutChannelRequest() {
        paymentCard.fail(INSTRUMENT, 409);

        assertNotSent(StandardApiNotSentCode.INTEGRATION_INITIALIZATION_FAILED, () -> execute(INSTRUMENT));

        assertAll(() -> assertTrue(channel.requests().isEmpty()), () -> assertEquals(1, paymentCard.reveals().size()));
    }

    /** card 返回的卡数据格式无效：同样未发出且不发出渠道请求，失败说明不回显卡数据。 */
    @Test
    void test_invalidCardWithoutChannelRequest() {
        paymentCard.allow(INSTRUMENT, "4111", "12", "2030", HOLDER, CVV);

        var failure = assertNotSent(StandardApiNotSentCode.INTEGRATION_INITIALIZATION_FAILED,
            () -> execute(INSTRUMENT));

        assertAll(() -> assertTrue(channel.requests().isEmpty()),
            () -> assertFalse(String.valueOf(failure.getMessage()).contains("4111"), "失败说明包含卡数据"));
    }

    /** 沙箱验证：测试卡来自验证账号、不调用 card；渠道收到原值，证据与库中轨迹只有遮蔽文本。 */
    @Test
    void test_verifyRunMasksCardEvidence() {
        cardIntegration(SANDBOX, true);
        var credentials = new LinkedHashMap<String, String>();
        credentials.put("privateKey", account.path("credentials").path("privateKey").asString());
        credentials.put(VerifyPaymentCardFunction.PREFIX + "pan", PAN);
        credentials.put(VerifyPaymentCardFunction.PREFIX + "expiryMonth", "12");
        credentials.put(VerifyPaymentCardFunction.PREFIX + "expiryYear", "2030");
        credentials.put(VerifyPaymentCardFunction.PREFIX + "cardholderName", HOLDER);
        credentials.put(VerifyPaymentCardFunction.PREFIX + "cvv", CVV);
        post(BASE + "/integrations/" + SANDBOX + "/verify-accounts", new CreateVerifyAccountRequestBody("MAIN",
            "测试卡账号", "DEMO", attempt(), attempt(), Map.of(), credentials, "卡直付验证")).assertOk();
        post(BASE + "/integrations/" + SANDBOX + "/verify-cases", new CreateVerifyCaseRequestBody("CARD_PAY",
            "卡支付", null, "MAIN", ExternalApiSamples.object("""
                {"steps":[{"id":"pay","standardApiCode":"%s",
                  "input":{"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s","instrumentToken":"unused"}},
                  "expect":{"executeStatus":"SUCCESS"}}]}""".formatted(PAY, attempt(), VAULT_TOKEN)).get("steps"),
            "卡直付验证")).assertOk();
        channel.respond("POST", PATH, 200, "{\"code\":\"APPLY_SUCCESS\",\"data\":{\"tradeToken\":\"T-VERIFY\"}}");

        var started = post(BASE + "/integrations/" + SANDBOX + "/verify-cases/CARD_PAY/runs",
            new StartVerifyRunRequestBody("卡直付验证")).assertOk().as(VerifyRunStartResponseBody.class);
        runs.awaitIdle();
        await().atMost(Duration.ofSeconds(5)).until(() -> !VerifyRunStatus.RUNNING.getCode()
            .equals(runDetail(started.getRunCode()).getRunStatus()));
        VerifyRunResponseBody run = runDetail(started.getRunCode());

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

    /** 渠道：签名脚本、卡下单原子 API（五个卡字段）与读卡流程。 */
    private void cardIntegration(String code, boolean sandbox) {
        Channel integration =
            externalApi.channel(code, mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api", sandbox);
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

    /** 以合成支付上下文与读卡票据经进程内入口执行卡支付标准 API。 */
    private ExecutionResult execute(String instrumentToken) {
        return execute(PAY, CODE, ExternalApiSamples.object("""
            {"paymentContext":{"attemptId":"%s","amount":"10.00","vaultToken":"%s","instrumentToken":"%s"}}"""
            .formatted(attempt(), VAULT_TOKEN, instrumentToken)));
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

    /** BODY 字段。 */
    private static ApiFieldBody field(String path, ApiFieldDataType type, boolean required) {
        return ExternalApiSamples.field(ApiFieldLocation.BODY, path, type, required);
    }

    /** 随机生成的合成商户 RSA 密钥对。 */
    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
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

    /** 标准 Base64 编码。 */
    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
