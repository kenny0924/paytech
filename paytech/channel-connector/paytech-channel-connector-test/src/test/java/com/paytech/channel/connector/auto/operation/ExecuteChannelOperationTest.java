package com.paytech.channel.connector.auto.operation;

import static org.junit.jupiter.api.Assertions.*;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.card.api.CardApiConstants;
import com.paytech.channel.connector.api.enums.ApiFieldDataType;
import com.paytech.channel.connector.api.enums.ApiFieldLocation;
import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.api.enums.ChannelPaymentStatus;
import com.paytech.channel.connector.api.enums.ChannelType;
import com.paytech.channel.connector.api.enums.ExecutionPurpose;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;
import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;
import com.paytech.channel.connector.api.operation.ChannelOperationApi;
import com.paytech.channel.connector.api.operation.request.ExecuteChannelOperationRequestBody;
import com.paytech.channel.connector.api.operation.response.ChannelOperationResponseBody;
import com.paytech.channel.connector.domain.PayOutputs;
import com.paytech.channel.connector.protocol.core.dal.dataobject.Channel;
import com.paytech.channel.connector.protocol.core.dal.dataobject.StandardApi;
import com.paytech.channel.connector.protocol.core.enums.ChannelStatus;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.ParameterDeclaration;
import com.paytech.channel.connector.test.base.ChannelConnectorMockTestBase;
import com.paytech.channel.connector.test.builder.ChannelRequestBuilder;
import com.paytech.channel.connector.test.builder.ExternalApiFixtures;
import com.paytech.channel.connector.test.builder.ExternalApiSamples;
import com.paytech.channel.connector.test.dal.ChannelTestMapper;
import com.paytech.channel.connector.test.mock.ChannelMockController;
import com.paytech.channel.connector.test.mocker.AccessLogProbe;
import com.paytech.channel.connector.test.mocker.ChannelDataMocker.Fixture;
import com.paytech.channel.connector.test.mocker.ChannelMocker;
import com.paytech.channel.connector.test.mocker.ExternalApiMocker;
import com.paytech.channel.connector.test.mocker.PaymentCardMocker;

import tools.jackson.databind.node.ObjectNode;

/**
 * 渠道操作执行（D06；2026-10-20 统一对外出口第一段改为进程内执行）：从 Payment 调用的真实 HTTP 入口经冻结坐标校验，
 * 在事务外调用 protocol 标准 API 执行引擎；引擎以 channel-connector-service 身份经 Vault Mock 读取渠道账号配置、经 card Mock
 * 凭票据读卡，再把请求发到合成外部渠道；channel-connector 把执行结果映射为已映射结论（PayOutputs）。
 *
 * <p>
 * 覆盖：SUCCEEDED / PENDING（跳转地址与交互类型）/ 受理后 FAILED / 渠道 FAIL / 无法识别的支付状态；原交易查单 QUERY → 标准 API
 * PAY_QUERY（3DS 全链路 technique 3.3.2：SUCCEEDED 带金额币种、CLOSED、处理中、FAIL + NOT_FOUND，PAY 不承认 CLOSED）；引擎在第一个节点前的未发出失败
 * （标准 API 或实现不存在、配置无效、初始化失败、预算耗尽、输入不满足请求字段）→ NOT_SENT 且原因代码为未发出代码；
 * 渠道已收到请求后的错误、断连与超时 → ERROR；OE 不一致与账号未配置在发出前 NOT_SENT；请求校验失败不进入引擎。
 *
 * @see ChannelOperationApi#execute(ExecuteChannelOperationRequestBody)
 */
class ExecuteChannelOperationTest extends ChannelConnectorMockTestBase {
    private static final String REDIRECT_URL = "https://3ds.local.invalid/challenge?token=transient-3ds";
    private static final String INSTRUMENT = ChannelRequestBuilder.CARD_TICKET;
    private static final String PAN = "4111111111111111";
    private static final String CVV = "737";
    private static final String CALL = "CARD_PAY";
    private static final String PAYMENTS = "/api/merchants/synthetic-outlet/payments";
    private static final String FLOW = "operation-card-pay";
    private static final String QUERY_CALL = "CARD_QUERY";
    private static final String QUERY_FLOW = "operation-card-query";
    private static final String QUERY_PATH = PAYMENTS + "/9001";

    @LocalServerPort
    private int port;

    @Autowired
    private ChannelTestMapper mapper;

    @Autowired
    private ExternalApiMocker externalApi;

    @Autowired
    private AccessLogProbe accessLog;

    private Fixture fixture;

    /** 本类公共：OE 00001 的启用三元组（渠道 NI）与 CARD/AED PAY 能力，账号 Vault 配置与合成测试卡。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.activeFixture();
        vault.configure(fixture);
        paymentCard.allow(INSTRUMENT, PAN, "12", "2030", "TEST HOLDER", CVV);
    }

    /**
     * 收款成功：引擎经 Vault 读取冻结账号配置、以本服务身份读卡，渠道收到卡值与 Bearer 认证；结论为 SUCCESS + SUCCEEDED，
     * 带渠道交易编号与原始状态，全部外部调用在事务外。
     */
    @Test
    void test_succeededMapped() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-1","paymentStatus":"SUCCEEDED","state":"SUCCESS"}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        var request = channel.only();
        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.SUCCEEDED.getCode(), result.getPaymentStatus()),
            () -> assertEquals("T-CC-1", result.getChannelTransactionRef()),
            () -> assertEquals("SUCCESS", result.getChannelStatus()), () -> assertNull(result.getFailureCode()),
            () -> assertNull(result.getRedirectUrl()),
            () -> assertEquals(List.of("channelStatus", "channelTransactionRef", "paymentStatus"),
                result.getRawOutputKeys()),
            () -> assertEquals("Bearer synthetic-api-key", request.headers().get("authorization")),
            () -> assertTrue(request.body().contains("\"orderRef\":\"9001\""), request.body()),
            () -> assertTrue(request.body().contains("\"amount\":\"10.00\""), request.body()),
            () -> assertTrue(request.body().contains("\"currency\":\"AED\""), request.body()),
            () -> assertTrue(request.body().contains("\"number\":\"" + PAN + "\""), "渠道应收到卡号"),
            () -> assertEquals(List.of(new PaymentCardMocker.Reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE,
                INSTRUMENT)), paymentCard.reveals()));
        vault.assertOnly(fixture.getAccount().getAccountVaultToken());
        transactions.assertOutsideTransactions();
        var line = accessLog.lines().getFirst();
        assertAll(() -> assertEquals(1, accessLog.lines().size()),
            () -> assertTrue(line.contains("callerService=payment-service"), line),
            () -> assertTrue(line.contains("channelType=PAYMENT"), line),
            () -> assertTrue(line.contains("apiCode=" + CALL), line),
            () -> assertFalse(line.contains(PAN), "访问日志不得含卡号"),
            () -> assertFalse(line.contains("synthetic-api-key"), "访问日志不得含凭据"));
    }

    /** 待认证：SUCCESS + PENDING，跳转地址取自 nextAction.redirectUrl，渠道未给交互类型时为 REDIRECT，原始状态与交易编号一并返回。 */
    @Test
    void test_pendingWithRedirect() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-3DS","paymentStatus":"PENDING","state":"PENDING","redirectUrl":"%s"}"""
            .formatted(REDIRECT_URL));

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.PENDING.getCode(), result.getPaymentStatus()),
            () -> assertEquals(REDIRECT_URL, result.getRedirectUrl()),
            () -> assertEquals(PayOutputs.REDIRECT, result.getNextActionType()),
            () -> assertEquals("T-CC-3DS", result.getChannelTransactionRef()),
            () -> assertEquals("PENDING", result.getChannelStatus()));
    }

    /** 渠道给出 nextAction.type 时如实返回（3DS 全链路 R2）；合同外的类型按 REDIRECT。 */
    @Test
    void test_pendingWithChallengeType() {
        ObjectNode flow = ExternalApiFixtures.flow(FLOW);
        ((ObjectNode)flow.path("returns").get(0).path("outputs")).putObject("nextAction.type").put("kind", "PATH")
            .put("path", "steps.pay.outputs.state");
        implement(flow);
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-3DS","paymentStatus":"PENDING","state":"THREE_DS_CHALLENGE","redirectUrl":"%s"}"""
            .formatted(REDIRECT_URL))
            .respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-APP","paymentStatus":"PENDING","state":"APP_SWITCH","redirectUrl":"%s"}"""
            .formatted(REDIRECT_URL));

        var challenge = execute(ChannelRequestBuilder.operationBody(fixture));
        var other = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals("THREE_DS_CHALLENGE", challenge.getNextActionType()),
            () -> assertEquals(REDIRECT_URL, challenge.getRedirectUrl()),
            () -> assertEquals(PayOutputs.REDIRECT, other.getNextActionType()));
    }

    /** PAY 输出 CLOSED / NOT_FOUND 不属于 PAY 合同：支付状态为空，由调用方按结果未知处理。 */
    @Test
    void test_payDoesNotAcceptQueryStatuses() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-C","paymentStatus":"CLOSED"}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertNull(result.getPaymentStatus()), () -> assertNull(result.getAmount()));
    }

    /**
     * 原交易查单成功：QUERY + ORIGINAL_TRANSACTION 执行标准 API PAY_QUERY（不是 QUERY），渠道收到 GET 查单；
     * 结论 SUCCESS + SUCCEEDED，带渠道交易编号与渠道记录的金额、币种，没有交互指令；不读卡。
     */
    @Test
    void test_querySucceededWithAmount() {
        implementQuery();
        channel.respond("GET", QUERY_PATH, 200, """
            {"result":"FOUND","reference":"T-Q-1","paymentStatus":"SUCCEEDED","state":"SUCCESS","amount":"10.00","currency":"AED"}""");

        var result = execute(queryBody());

        var request = channel.only();
        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.SUCCEEDED.getCode(), result.getPaymentStatus()),
            () -> assertEquals("T-Q-1", result.getChannelTransactionRef()),
            () -> assertEquals("10.00", result.getAmount()), () -> assertEquals("AED", result.getCurrency()),
            () -> assertNull(result.getRedirectUrl()), () -> assertNull(result.getNextActionType()),
            () -> assertEquals("GET", request.method()),
            () -> assertEquals("Bearer synthetic-api-key", request.headers().get("authorization")),
            () -> assertTrue(paymentCard.reveals().isEmpty(), "查单不读卡"));
        transactions.assertOutsideTransactions();
        assertTrue(accessLog.lines().getFirst().contains("apiCode=" + QUERY_CALL), accessLog.lines().getFirst());
    }

    /** 查单得到渠道订单已关闭与仍在处理：SUCCESS + CLOSED / PENDING。 */
    @Test
    void test_queryClosedAndPending() {
        implementQuery();
        channel.respond("GET", QUERY_PATH, 200, """
            {"result":"FOUND","reference":"T-Q-C","paymentStatus":"CLOSED","state":"CLOSED","amount":"10.00","currency":"AED"}""")
            .respond("GET", QUERY_PATH, 200, """
            {"result":"FOUND","reference":"T-Q-P","paymentStatus":"PENDING","state":"PENDING"}""");

        var closed = execute(queryBody());
        var pending = execute(queryBody());

        assertAll(() -> assertEquals(ChannelPaymentStatus.CLOSED.getCode(), closed.getPaymentStatus()),
            () -> assertEquals(ChannelPaymentStatus.PENDING.getCode(), pending.getPaymentStatus()),
            () -> assertNull(pending.getAmount()), () -> assertNull(pending.getCurrency()));
    }

    /** 渠道无此交易：FAIL + NOT_FOUND，失败代码取自渠道。 */
    @Test
    void test_queryNotFound() {
        implementQuery();
        channel.respond("GET", QUERY_PATH, 404, """
            {"errorCode":"ORDER_NOT_EXIST"}""");

        var result = execute(queryBody());

        assertAll(() -> assertEquals(ChannelOperationOutcome.FAIL.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.NOT_FOUND.getCode(), result.getPaymentStatus()),
            () -> assertEquals("ORDER_NOT_EXIST", result.getFailureCode()));
    }

    /** 查单只接受原交易用途：QUERY + NEW_PAYMENT 返回 400，不进入引擎。 */
    @Test
    void test_queryRequiresOriginalTransaction() {
        var body = queryBody();
        body.setPurpose(ExecutionPurpose.NEW_PAYMENT.getCode());
        body.setCriteria(ChannelRequestBuilder.operationBody(fixture).getCriteria());
        body.setAmount("10.00");

        assertEquals(400, status(body));
        assertNoExternalCalls();
    }

    /** 受理后拒绝：SUCCESS + FAILED，渠道失败代码与说明原样返回。 */
    @Test
    void test_declinedAfterAcceptance() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-D","paymentStatus":"FAILED","state":"FAILED",
             "errorCode":"ISSUER_PAYMENT_REJECTED","errorMessage":"Transaction failed at issuer end."}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), result.getOutcome()),
            () -> assertEquals(ChannelPaymentStatus.FAILED.getCode(), result.getPaymentStatus()),
            () -> assertEquals("ISSUER_PAYMENT_REJECTED", result.getFailureCode()),
            () -> assertEquals("Transaction failed at issuer end.", result.getFailureMessage()),
            () -> assertEquals("T-CC-D", result.getChannelTransactionRef()));
    }

    /** 渠道明确拒绝（引擎执行状态 FAIL）：outcome FAIL，失败代码取自输出，没有支付状态。 */
    @Test
    void test_channelRejected() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"REJECTED","state":"REJECTED","errorCode":"CARD_BLOCKED","errorMessage":"Card is blocked."}""");

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.FAIL.getCode(), result.getOutcome()),
            () -> assertNull(result.getPaymentStatus()), () -> assertEquals("CARD_BLOCKED", result.getFailureCode()),
            () -> assertEquals("REJECTED", result.getChannelStatus()),
            () -> assertEquals("Card is blocked.", result.getFailureMessage()));
    }

    /** 受理但支付状态无法识别：SUCCESS 且 paymentStatus 为空，由调用方按结果未知处理；渠道响应不满足任何状态规则为 ERROR。 */
    @Test
    void test_unrecognizedStatusNotGuessed() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.respond("POST", PAYMENTS, 200, """
            {"result":"ACCEPTED","reference":"T-CC-U","paymentStatus":"SETTLING"}""")
            .respond("POST", PAYMENTS, 500, "{\"message\":\"internal\"}");

        var unrecognized = execute(ChannelRequestBuilder.operationBody(fixture));
        var unknownStatus = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.SUCCESS.getCode(), unrecognized.getOutcome()),
            () -> assertNull(unrecognized.getPaymentStatus()),
            () -> assertEquals("T-CC-U", unrecognized.getChannelTransactionRef()),
            () -> assertEquals(ChannelOperationOutcome.ERROR.getCode(), unknownStatus.getOutcome()),
            () -> assertNull(unknownStatus.getFailureCode()),
            () -> assertEquals(List.of(), unknownStatus.getRawOutputKeys()),
            () -> assertEquals(2, channel.requests().size()));
    }

    /** 请求已发出后渠道断连或超过原子 API 响应超时：结果未知 ERROR，不猜测成功或失败。 */
    @Test
    void test_sentButUnansweredIsUnknown() {
        implement(ExternalApiFixtures.flow(FLOW));
        channel.reply("POST", PAYMENTS, ChannelMocker.Reply.disconnected())
            .reply("POST", PAYMENTS, ChannelMocker.Reply.delayed(200, """
                {"result":"ACCEPTED","reference":"T-LATE","paymentStatus":"SUCCEEDED"}""", 1500));

        var disconnected = execute(ChannelRequestBuilder.operationBody(fixture));
        var timedOut = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.ERROR.getCode(), disconnected.getOutcome()),
            () -> assertEquals(ChannelOperationOutcome.ERROR.getCode(), timedOut.getOutcome()),
            () -> assertNull(timedOut.getChannelTransactionRef()), () -> assertNull(timedOut.getFailureCode()),
            () -> assertEquals(2, channel.requests().size()));
    }

    /** 标准 API PAY 不存在：引擎未发出，NOT_SENT 且原因代码为 STANDARD_API_NOT_FOUND；不读 Vault、不读卡。 */
    @Test
    void test_standardApiMissingNotSent() {
        assertNotSent(StandardApiNotSentCode.STANDARD_API_NOT_FOUND, execute(ChannelRequestBuilder.operationBody(fixture)));
        vault.assertNoCalls();
    }

    /** 渠道编码对应的渠道没有实现 PAY：NOT_SENT 且原因代码为 INTEGRATION_IMPLEMENTATION_NOT_FOUND。 */
    @Test
    void test_implementationMissingNotSent() {
        externalApi.standardApi(ChannelOperation.PAY.getCode(), requestFields(), responseFields());
        externalApi.channel(fixture.getChannel().getChannelCode(), channelBaseUrl());

        assertNotSent(StandardApiNotSentCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND,
            execute(ChannelRequestBuilder.operationBody(fixture)));
        vault.assertNoCalls();
    }

    /** 已保存流程未通过运行前校验：NOT_SENT 且原因代码为 INTEGRATION_CONFIG_INVALID。 */
    @Test
    void test_configInvalidNotSent() {
        implement(ExternalApiSamples.object("{\"timeoutMs\":0}"));

        assertNotSent(StandardApiNotSentCode.INTEGRATION_CONFIG_INVALID,
            execute(ChannelRequestBuilder.operationBody(fixture)));
        vault.assertNoCalls();
    }

    /** 必需初始化项读卡被 card 拒绝：NOT_SENT 且原因代码为 INTEGRATION_INITIALIZATION_FAILED，渠道没有收到请求。 */
    @Test
    void test_initializationFailedNotSent() {
        implement(ExternalApiFixtures.flow(FLOW));
        paymentCard.fail(INSTRUMENT, 409);

        assertNotSent(StandardApiNotSentCode.INTEGRATION_INITIALIZATION_FAILED,
            execute(ChannelRequestBuilder.operationBody(fixture)));
        assertEquals(1, paymentCard.reveals().size());
    }

    /** 初始化用尽流程预算：NOT_SENT 且原因代码为 INTEGRATION_BUDGET_EXHAUSTED，渠道没有收到请求。 */
    @Test
    void test_budgetExhaustedNotSent() {
        ObjectNode flow = ExternalApiFixtures.flow(FLOW).put("timeoutMs", 50);
        implement(flow);
        vault.delay(fixture.getAccount().getAccountVaultToken(), 200);

        assertNotSent(StandardApiNotSentCode.INTEGRATION_BUDGET_EXHAUSTED,
            execute(ChannelRequestBuilder.operationBody(fixture)));
    }

    /** 业务输入含标准 API 未声明的字段：NOT_SENT 且原因代码为 VALIDATION_ERROR；不读 Vault、不读卡。 */
    @Test
    void test_inputInvalidNotSent() {
        implement(ExternalApiFixtures.flow(FLOW));
        var body = ChannelRequestBuilder.operationBody(fixture);
        body.getInput().put("unexpected", "x");

        assertNotSent(StandardApiNotSentCode.VALIDATION_ERROR, execute(body));
        vault.assertNoCalls();
    }

    /** OE 一致性（热点 10）：请求 OE 与账号 / MID 不一致时 NOT_SENT，不进入引擎，绝不重选三元组。 */
    @Test
    void test_entityMismatchNotSent() {
        implement(ExternalApiFixtures.flow(FLOW));
        var otherEntity = ChannelRequestBuilder.operationBody(fixture);
        otherEntity.setEntityNo("00002");
        var requestOtherOe = execute(otherEntity);
        mapper.accountEntity("00009");

        var accountOtherOe = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), requestOtherOe.getOutcome()),
            () -> assertEquals("CHANNEL_TARGET_UNAVAILABLE", requestOtherOe.getFailureCode()),
            () -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), accountOtherOe.getOutcome()),
            () -> assertEquals("CHANNEL_TARGET_UNAVAILABLE", accountOtherOe.getFailureCode()));
        assertNoExternalCalls();
    }

    /** 冻结渠道是服务渠道：支付入口一律拒绝，按“配置无效”返回 NOT_SENT（INTEGRATION_CONFIG_INVALID），不进入引擎（AC-06）。 */
    @Test
    void test_serviceChannelRejected() {
        implement(ExternalApiFixtures.flow(FLOW));
        Channel service = data.channel("GMAP", "合成服务渠道", ChannelType.SERVICE, ChannelStatus.ACTIVE);
        var account = data.account(service.getChannelId(), "00001", "C".repeat(43));
        var mid = data.mid(service.getChannelId(), account.getChannelAccountId(), "00001");

        var result = execute(ChannelRequestBuilder.operationBody(new Fixture(service, account, mid)));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), result.getOutcome()),
            () -> assertEquals(StandardApiNotSentCode.INTEGRATION_CONFIG_INVALID.getCode(), result.getFailureCode()));
        assertNoExternalCalls();
    }

    /** 冻结账号没有 Vault 配置引用：NOT_SENT，不进入引擎。 */
    @Test
    void test_accountNotConfiguredNotSent() {
        implement(ExternalApiFixtures.flow(FLOW));
        mapper.clearAccountToken();

        var result = execute(ChannelRequestBuilder.operationBody(fixture));

        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), result.getOutcome()),
            () -> assertEquals("CHANNEL_ACCOUNT_NOT_CONFIGURED", result.getFailureCode()));
        assertNoExternalCalls();
    }

    /** 用途与操作不符返回 400，缺少输入或 ID 非法返回 422；均不进入引擎。 */
    @Test
    void test_invalidRequestRejected() {
        var wrongPurpose = ChannelRequestBuilder.operationBody(fixture);
        wrongPurpose.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION.getCode());
        var missingInput = ChannelRequestBuilder.operationBody(fixture);
        missingInput.setInput(null);
        var numericLikeId = ChannelRequestBuilder.operationBody(fixture);
        numericLikeId.setMidId("0");

        assertAll(() -> assertEquals(400, status(wrongPurpose)), () -> assertEquals(422, status(missingInput)),
            () -> assertEquals(422, status(numericLikeId)));
        assertNoExternalCalls();
    }

    /** 渠道编码即渠道编码：标准 API PAY、渠道（指向 Mock 渠道）、Bearer 脚本、卡下单原子 API 与指定流程。 */
    private void implement(ObjectNode flow) {
        StandardApi pay = externalApi.standardApi(ChannelOperation.PAY.getCode(), requestFields(), responseFields());
        Channel integration = externalApi.channel(fixture.getChannel().getChannelCode(), channelBaseUrl());
        externalApi.script(integration, "BEARER", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("token", "PROTECTED_HANDLE", true)), ExternalApiFixtures.script("bearer"));
        externalApi.integrationApi(integration, CALL, ExternalApiFixtures.definition(FLOW),
            List.of(field(ApiFieldLocation.PATH, "merchantId", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "orderRef", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "amount", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "currency", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "returnUrl", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "card", ApiFieldDataType.OBJECT, false),
                field(ApiFieldLocation.BODY, "card.number", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "card.expMonth", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "card.expYear", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "card.cvv", ApiFieldDataType.STRING, true)),
            List.of(field(ApiFieldLocation.BODY, "result", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "reference", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "paymentStatus", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "state", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "redirectUrl", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "errorCode", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "errorMessage", ApiFieldDataType.STRING, false)));
        externalApi.implementation(integration, pay, flow);
    }

    /** 标准 API PAY_QUERY、查单原子 API 与流程（渠道、Bearer 脚本同 PAY）。 */
    private void implementQuery() {
        StandardApi query = externalApi.standardApi(ChannelOperation.QUERY.getStandardApiCode(),
            List.of(field(ApiFieldLocation.BODY, "payment", ApiFieldDataType.OBJECT, true),
                field(ApiFieldLocation.BODY, "payment.attemptId", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "channelAccount", ApiFieldDataType.OBJECT, true),
                field(ApiFieldLocation.BODY, "channelAccount.channelCode", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "channelAccount.channelAccountId", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "channelAccount.entityNo", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.BODY, "channelAccount.accountVaultToken", ApiFieldDataType.STRING, true)),
            List.of(field(ApiFieldLocation.BODY, "paymentStatus", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "channelTransactionRef", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "channelStatus", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "amount", ApiFieldDataType.OBJECT, false),
                field(ApiFieldLocation.BODY, "amount.value", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "amount.currency", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "failureCode", ApiFieldDataType.STRING, false)));
        Channel integration = externalApi.channel(fixture.getChannel().getChannelCode(), channelBaseUrl());
        externalApi.script(integration, "BEARER", ScriptExecutionStage.REQUEST,
            List.of(new ParameterDeclaration("token", "PROTECTED_HANDLE", true)), ExternalApiFixtures.script("bearer"));
        externalApi.integrationApi(integration, QUERY_CALL, ExternalApiFixtures.definition(QUERY_FLOW),
            List.of(field(ApiFieldLocation.PATH, "merchantId", ApiFieldDataType.STRING, true),
                field(ApiFieldLocation.PATH, "orderRef", ApiFieldDataType.STRING, true)),
            List.of(field(ApiFieldLocation.BODY, "result", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "reference", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "paymentStatus", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "state", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "amount", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "currency", ApiFieldDataType.STRING, false),
                field(ApiFieldLocation.BODY, "errorCode", ApiFieldDataType.STRING, false)));
        externalApi.implementation(integration, query, ExternalApiFixtures.flow(QUERY_FLOW));
    }

    /** 原交易查单请求：冻结三元组、QUERY + ORIGINAL_TRANSACTION，只带支付尝试 ID，没有交易维度与金额。 */
    private ExecuteChannelOperationRequestBody queryBody() {
        var body = ChannelRequestBuilder.operationBody(fixture);
        body.setOperation(ChannelOperation.QUERY.getCode());
        body.setPurpose(ExecutionPurpose.ORIGINAL_TRANSACTION.getCode());
        body.setCriteria(null);
        body.setAmount(null);
        body.setInput(new LinkedHashMap<>(Map.of("payment", Map.of("attemptId", "9001"))));
        return body;
    }

    /** 标准 API PAY 的请求字段：Payment 传入的支付工具与支付信息，以及 channel-connector 补上的冻结账号身份。 */
    private static List<ApiFieldBody> requestFields() {
        return List.of(field(ApiFieldLocation.BODY, "card", ApiFieldDataType.OBJECT, true),
            field(ApiFieldLocation.BODY, "card.token", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "payment", ApiFieldDataType.OBJECT, true),
            field(ApiFieldLocation.BODY, "payment.attemptId", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "payment.amount", ApiFieldDataType.OBJECT, true),
            field(ApiFieldLocation.BODY, "payment.amount.value", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "payment.amount.currency", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "payment.returnUrl", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "channelAccount", ApiFieldDataType.OBJECT, true),
            field(ApiFieldLocation.BODY, "channelAccount.channelCode", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "channelAccount.channelAccountId", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "channelAccount.entityNo", ApiFieldDataType.STRING, true),
            field(ApiFieldLocation.BODY, "channelAccount.accountVaultToken", ApiFieldDataType.STRING, true));
    }

    /** 标准 API PAY 的响应字段（PayOutputs 读取的字段）；渠道拒绝出口没有支付状态，因此全部可选。 */
    private static List<ApiFieldBody> responseFields() {
        return List.of(field(ApiFieldLocation.BODY, "paymentStatus", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "channelTransactionRef", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "channelStatus", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "failureCode", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "failureMessage", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "nextAction", ApiFieldDataType.OBJECT, false),
            field(ApiFieldLocation.BODY, "nextAction.redirectUrl", ApiFieldDataType.STRING, false),
            field(ApiFieldLocation.BODY, "nextAction.type", ApiFieldDataType.STRING, false));
    }

    /** 构造字段，显示名称同路径。 */
    private static ApiFieldBody field(ApiFieldLocation location, String path, ApiFieldDataType type, boolean required) {
        return ExternalApiSamples.field(location, path, type, required);
    }

    /** 渠道出站地址：本机 Mock 渠道。 */
    private String channelBaseUrl() {
        return mockHttpServer.baseUrl() + ChannelMockController.PREFIX + "/api";
    }

    /** 未发出：NOT_SENT、原因代码为引擎未发出代码、没有支付状态，渠道没有收到请求。 */
    private void assertNotSent(StandardApiNotSentCode code, ChannelOperationResponseBody result) {
        assertAll(() -> assertEquals(ChannelOperationOutcome.NOT_SENT.getCode(), result.getOutcome()),
            () -> assertEquals(code.getCode(), result.getFailureCode()), () -> assertNull(result.getPaymentStatus()),
            () -> assertTrue(channel.requests().isEmpty(), "渠道不应收到请求"));
    }

    /** 引擎之前就结束：没有 Vault 读取、读卡与渠道请求。 */
    private void assertNoExternalCalls() {
        vault.assertNoCalls();
        assertAll(() -> assertTrue(paymentCard.reveals().isEmpty(), "不应读卡"),
            () -> assertTrue(channel.requests().isEmpty(), "渠道不应收到请求"));
    }

    /** 经真实 HTTP 入口执行；4xx/5xx 使测试失败。 */
    private ChannelOperationResponseBody execute(ExecuteChannelOperationRequestBody body) {
        return client().post().uri("/channel-connector/v1/operations").contentType(MediaType.APPLICATION_JSON).body(body)
            .retrieve().body(ChannelOperationResponseBody.class);
    }

    /** 执行并返回 HTTP 状态。 */
    private int status(ExecuteChannelOperationRequestBody body) {
        try {
            return client().post().uri("/channel-connector/v1/operations").contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** JDK 客户端：不自动重试非幂等 POST。 */
    private RestClient client() {
        return RestClient.builder().baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())).build();
    }
}
