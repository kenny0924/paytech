package com.paytech.channel.connector.auto.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.builder.ChannelRequestBuilder;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 渠道操作执行（D06）：从 channel-connector 的真实 HTTP 入口经冻结坐标校验，以真实 Platform 客户端调用 Platform 测试 HTTP 服务，
 * 由 channel-connector 把 Platform 输出与 Problem 代码映射为已映射结论（迁自 Payment PayActionActor / PayOutputs 与
 * PlatformStandardApiClient）。
 *
 * <p>
 * 覆盖：SUCCEEDED / PENDING（跳转地址）/ 受理后 FAILED / 渠道 FAIL / 无法识别的状态；Platform 保证未发出的 Problem → NOT_SENT，
 * 其他错误 → ERROR；OE 不一致与账号未配置在发出前 NOT_SENT；请求校验失败不调用 Platform。
 *
 * @see ChannelOperationApi#execute(ExecuteChannelOperationRequestBody)
 */
class ExecuteChannelOperationTest extends ChannelConnectorMockTestBase {
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelTestMapper mapper;

    private Fixture fixture;

    /** 本类公共：OE 00001 的启用三元组与 CARD/AED PAY 能力。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
    }

    /** 收款成功：Platform 收到冻结账号身份与原样的支付工具输入；结论为 SUCCESS + SUCCEEDED，带渠道交易编号与原始状态。 */
    @Test
    void test_succeededMapped() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-CC-1","channelStatus":"SUCCESS"}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        var call = platform.only();
        var account = call.input().path("channelAccount");
        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.SUCCEEDED.getCode(), result.getPaymentStatus()),
            () -> assertEquals("T-CC-1", result.getChannelTransactionRef()),
            () -> assertEquals("SUCCESS", result.getChannelStatus()), () -> assertNull(result.getFailureCode()),
            () -> assertNull(result.getRedirectUrl()),
            () -> assertEquals(List.of("channelStatus", "channelTransactionRef", "paymentStatus"), result.getRawOutputKeys()),
            () -> assertEquals(ChannelOperation.PAY.getCode(), call.standardApiCode()),
            () -> assertEquals("NI", call.integrationCode()),
            () -> assertEquals(fixture.getAccount().getAccountVaultToken(), account.path("accountVaultToken").asString()),
            () -> assertEquals(fixture.getAccount().getChannelAccountId().toString(),
                account.path("channelAccountId").asString()),
            () -> assertEquals("00001", account.path("entityNo").asString()),
            () -> assertEquals("synthetic-instrument-token", call.input().path("card").path("token").asString()),
            () -> assertEquals("10.00", call.input().path("payment").path("amount").path("value").asString()));
        vault.assertNoCalls();
    }

    /** 待认证：SUCCESS + PENDING，跳转地址取自 nextAction.redirectUrl，原始状态与交易编号一并返回。 */
    @Test
    void test_pendingWithRedirect() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"PENDING","nextAction":{"type":"THREE_DS_CHALLENGE","redirectUrl":"%s"},
             "channelTransactionRef":"T-CC-3DS","channelStatus":"PENDING"}""".formatted(REDIRECT_URL));

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.PENDING.getCode(), result.getPaymentStatus()),
            () -> assertEquals(REDIRECT_URL, result.getRedirectUrl()),
            () -> assertEquals("T-CC-3DS", result.getChannelTransactionRef()),
            () -> assertEquals("PENDING", result.getChannelStatus()));
    }

    /** 受理后拒绝：SUCCESS + FAILED，渠道失败代码与说明原样返回。 */
    @Test
    void test_declinedAfterAcceptance() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"FAILED","channelTransactionRef":"T-CC-D","channelStatus":"FAILED",
             "failureCode":"ISSUER_PAYMENT_REJECTED","failureMessage":"Transaction failed at issuer end."}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.FAILED.getCode(), result.getPaymentStatus()),
            () -> assertEquals("ISSUER_PAYMENT_REJECTED", result.getFailureCode()),
            () -> assertEquals("Transaction failed at issuer end.", result.getFailureMessage()),
            () -> assertEquals("T-CC-D", result.getChannelTransactionRef()));
    }

    /** 渠道明确拒绝（executeStatus FAIL）：outcome FAIL，失败代码取自输出，没有支付状态。 */
    @Test
    void test_channelRejected() {
        platform.respond("FAIL", """
            {"channelStatus":"REJECTED","failureCode":"CARD_BLOCKED","failureMessage":"Card is blocked."}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.FAIL.getCode(), result.getOutcome()),
            () -> assertNull(result.getPaymentStatus()), () -> assertEquals("CARD_BLOCKED", result.getFailureCode()),
            () -> assertEquals("REJECTED", result.getChannelStatus()),
            () -> assertEquals("Card is blocked.", result.getFailureMessage()));
    }

    /** 受理但支付状态缺失或无法识别：SUCCESS 且 paymentStatus 为空，由调用方按结果未知处理；未知 executeStatus 为 ERROR。 */
    @Test
    void test_unrecognizedStatusNotGuessed() {
        platform.respond("SUCCESS", """
            {"paymentStatus":"SETTLING","channelTransactionRef":"T-CC-U"}""");
        var unrecognized = execute(ChannelRequestBuilder.operationBody(fixture));
        platform.respond("PARTIAL", "{}");

        var unknownStatus = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), unrecognized.getOutcome()),
            () -> assertNull(unrecognized.getPaymentStatus()),
            () -> assertEquals("T-CC-U", unrecognized.getChannelTransactionRef()),
            () -> assertEquals(ChannelOperationOutcome.ERROR.getCode(), unknownStatus.getOutcome()),
            () -> assertNull(unknownStatus.getFailureCode()), () -> assertEquals(2, platform.calls().size()));
    }

    /** Platform 合同保证未发出渠道请求的 Problem：NOT_SENT，原因代码为 Problem code。 */
    @ParameterizedTest
    @CsvSource({"404, STANDARD_API_NOT_FOUND", "404, INTEGRATION_IMPLEMENTATION_NOT_FOUND",
        "409, INTEGRATION_CONFIG_INVALID", "503, INTEGRATION_INITIALIZATION_FAILED", "429, INTEGRATION_BUDGET_EXHAUSTED",
        "422, VALIDATION_ERROR"})
    void test_problemMeansNotSent(int status, String code) {
        platform.fail(MockHttpFault.json(status, Map.of("type", "about:blank", "title", "Platform", "detail", "未发出",
            "code", code)));

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), result.getOutcome()),
            () -> assertEquals(code, result.getFailureCode()), () -> assertNull(result.getPaymentStatus()),
            () -> assertEquals(1, platform.calls().size()));
    }

    /** 其他 Platform 错误（500 非保证码）：结果未知 ERROR，不猜测成功或失败。 */
    @Test
    void test_otherProblemIsUnknown() {
        platform.fail(MockHttpFault.json(500, Map.of("type", "about:blank", "title", "Internal Server Error",
            "detail", "系统处理结果未知", "code", "INTERNAL_ERROR")));

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.ERROR.getCode(), result.getOutcome()),
            () -> assertNull(result.getFailureCode()), () -> assertEquals(List.of(), result.getRawOutputKeys()));
    }

    /** OE 一致性（热点 10）：请求 OE 与账号 / MID 不一致时 NOT_SENT，不调用 Platform，绝不重选三元组。 */
    @Test
    void test_entityMismatchNotSent() {
        var otherEntity = ChannelRequestBuilder.operationBody(fixture);
        otherEntity.setEntityNo("00002");
        var requestOtherOe = execute(otherEntity);
        mapper.accountEntity("00009");

        var accountOtherOe = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), requestOtherOe.getOutcome()),
            () -> assertEquals("CHANNEL_TARGET_UNAVAILABLE", requestOtherOe.getFailureCode()),
            () -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), accountOtherOe.getOutcome()),
            () -> assertEquals("CHANNEL_TARGET_UNAVAILABLE", accountOtherOe.getFailureCode()),
            () -> assertTrue(platform.calls().isEmpty()));
        vault.assertNoCalls();
    }

    /** 冻结账号没有 Vault 配置引用：NOT_SENT，不调用 Platform。 */
    @Test
    void test_accountNotConfiguredNotSent() {
        mapper.clearAccountToken();

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), result.getOutcome()),
            () -> assertEquals("CHANNEL_ACCOUNT_NOT_CONFIGURED", result.getFailureCode()),
            () -> assertTrue(platform.calls().isEmpty()));
    }

    /** 用途与操作不符返回 400，缺少输入或 ID 非法返回 422；均不调用 Platform。 */
    @Test
    void test_invalidRequestRejected() {
        var wrongPurpose = ChannelRequestBuilder.operationBody(fixture);
        wrongPurpose.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION.getCode());
        var missingInput = ChannelRequestBuilder.operationBody(fixture);
        missingInput.setInput(null);
        var numericLikeId = ChannelRequestBuilder.operationBody(fixture);
        numericLikeId.setMidId("0");

        assertAll(() -> assertEquals(400, status(wrongPurpose)), () -> assertEquals(422, status(missingInput)),
            () -> assertEquals(422, status(numericLikeId)), () -> assertTrue(platform.calls().isEmpty()));
    }

    /** 经真实 HTTP 入口执行；4xx/5xx 使测试失败。 */
    ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody body) {
        return client().post().uri("/channel-connector/v1/operations").contentType(MediaType.APPLICATION_JSON).body(body)
            .retrieve().body(ChannelOperationResponseBody.class);
    }

    /** 执行并返回 HTTP 状态。 */
    int status(ExecuteChannelOperationRequestBody body) {
        try {
            return client().post().uri("/channel-connector/v1/operations").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** JDK 客户端：不自动重试非幂等 POST。 */
    RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }
}
