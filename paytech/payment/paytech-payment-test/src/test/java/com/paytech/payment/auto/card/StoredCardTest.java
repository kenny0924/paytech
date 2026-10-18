package com.paytech.payment.auto.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.PlatformMocker;

/**
 * 存卡与已存卡支付的 Payment 一侧（design/05 §2.3、§3；已存卡的生成、列出、删除与失效见 card 服务的 PaymentSucceededConsumeTest 与
 * StoredCardHttpTest）：经工具申请与 pay 的真实 HTTP 入口，card 为 card-api 测试 HTTP 服务，PaymentSucceeded 经本机 RabbitMQ 观察。
 *
 * <p>
 * 覆盖：Customer 与保存意愿随申请交给 card、存入 Token 行并随 PaymentSucceeded v2 交给 card 存卡；未同意或支付失败时不发布存卡意愿；
 * 不同 Client、同一 Customer 的申请各自独立；已存卡支付只把已存卡 Token 与本次 CVV 交给 card，渠道读到保存的卡值；归属与参数校验；
 * 已存卡接口已迁出 Payment；重放不可换卡。卡号均为公开测试卡号。
 */
class StoredCardTest extends AbstractPayCardTest {
    private static final String CUSTOMER_ID = "70001";
    private static final String OTHER_CUSTOMER_ID = "70002";
    private static final String OTHER_CLIENT_ID = "102";
    private static final String NEW_CVV = "4321";
    /** 每笔渠道交易号不同；渠道交易引用按交易号唯一。 */
    private int channelRefs;

    /** 本类公共：MID 候选、风控放行；渠道结论在每笔支付前配置。 */
    @BeforeEach
    void setUpClassMocks() {
        channel.candidate(ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 同意保存且支付成功：card 收到 Customer 与保存意愿，Token 行记录二者，PaymentSucceeded v2 把工具引用、同意与 Customer 交给 card。 */
    @Test
    void test_saveCardOnSuccess() {
        var paid = payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);

        var create = cardApi.creates().getFirst();
        var token = mapper.instrumentTokens().getFirst();
        long attemptId = Long.parseLong((String)paid.get("attemptId"));
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), paid.get("status")),
            () -> assertEquals(CUSTOMER_ID, create.getCustomerId()), () -> assertTrue(create.getConsentToSave()),
            () -> assertEquals(Long.parseLong(CUSTOMER_ID), ((Number)token.get("customerId")).longValue()),
            () -> assertTrue(CardStorageTest.consent(token)));
        results.awaitEnvelope(attemptId).cardFields((String)token.get("instrumentRef"), true, CUSTOMER_ID);
    }

    /** 未同意保存：v2 事件的 consentToSave 为 false；支付失败：不发布 PaymentSucceeded，card 不会存卡。 */
    @Test
    void test_noSaveWithoutConsentOrSuccess() {
        var saved = payManual(CLIENT_ID, CUSTOMER_ID, false, EXPIRY_YEAR);
        long savedAttempt = Long.parseLong((String)saved.get("attemptId"));
        results.awaitEnvelope(savedAttempt).cardFields((String)mapper.instrumentTokens().getFirst().get("instrumentRef"),
            false, CUSTOMER_ID);

        var declined = payManual(CLIENT_ID, OTHER_CUSTOMER_ID, true, "2031", this::declined);

        assertAll(() -> assertEquals(AttemptStatus.FAILED.getCode(), declined.get("status")),
            () -> results.assertNoSucceeded(Long.parseLong((String)declined.get("attemptId"))));
    }

    /** 同一 Customer 同卡换有效期再付：两笔各自以新工具申请并各发布一次存卡意愿，是否合并为同一张已存卡由 card 判断。 */
    @Test
    void test_saveSameCardUpdatesCredential() {
        awaitPublished(payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR));
        awaitPublished(payManual(CLIENT_ID, CUSTOMER_ID, true, "2031"));

        var tokens = mapper.instrumentTokens();
        assertAll(() -> assertEquals(List.of(EXPIRY_YEAR, "2031"),
            cardApi.creates().stream().map(create -> create.getCard().getExpiryYear()).toList()),
            () -> assertNotEquals(tokens.get(0).get("instrumentRef"), tokens.get(1).get("instrumentRef")),
            () -> assertEquals(tokens.get(0).get("cardFingerprint"), tokens.get(1).get("cardFingerprint")));
    }

    /** 每笔成功后立即读取其 payment.events 信封（探针会跳过并丢弃其他尝试的消息），确认各发布一次存卡意愿。 */
    void awaitPublished(Map<String, Object> paid) {
        long attemptId = Long.parseLong((String)paid.get("attemptId"));
        results.awaitEnvelope(attemptId).cardFields(latestInstrumentRef(), true, CUSTOMER_ID);
    }

    /** 最近一次申请的工具引用（本类用例按顺序申请与支付）。 */
    String latestInstrumentRef() {
        return (String)mapper.instrumentTokens().getLast().get("instrumentRef");
    }

    /** 同一张卡在两个 Client 下保存：card 分别收到各自的 Client，Token 行各自归属。 */
    @Test
    void test_tokenPerClient() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        payManual(OTHER_CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);

        assertEquals(List.of(CLIENT_ID, OTHER_CLIENT_ID),
            cardApi.creates().stream().map(create -> create.getClientId()).toList());
    }

    /** 用已存卡与新 CVV 支付：只把已存卡 Token、Customer 与本次 CVV 交给 card（无卡号），渠道凭票据读到保存的卡值与本次 CVV。 */
    @Test
    void test_payWithStoredCard() {
        String storedCardToken = cardApi.storedCard(CLIENT_ID, CUSTOMER_ID, PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME);

        var paid = payStored(CUSTOMER_ID, storedCardToken);

        var create = cardApi.creates().getFirst();
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), paid.get("status")),
            () -> assertNull(create.getCard(), "已存卡支付不转交卡号"),
            () -> assertEquals(storedCardToken, create.getStoredCard().getStoredCardToken()),
            () -> assertEquals(NEW_CVV, create.getStoredCard().getCvv()),
            () -> assertEquals(CUSTOMER_ID, create.getCustomerId()), () -> assertFalse(create.getConsentToSave()),
            () -> assertEquals(PlatformMocker.materialDigest(PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME, NEW_CVV),
                channel.only().materialDigest(), "渠道读到的卡值与已存卡或本次 CVV 不一致"));
        long attemptId = Long.parseLong((String)paid.get("attemptId"));
        results.awaitEnvelope(attemptId);
    }

    /** 已存卡不属于该 Customer：card 拒绝 400；缺 Customer、同时给卡与已存卡、已存卡再选保存：Payment 请求校验 422，不调用 card。均不写 Token。 */
    @Test
    void test_storedCardRejected() {
        String storedCardToken = cardApi.storedCard(CLIENT_ID, CUSTOMER_ID, PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME);

        int otherCustomer = applyStatus(newIntent(), storedBody(OTHER_CUSTOMER_ID, storedCardToken));
        int noCustomer = applyStatus(newIntent(), storedBody(null, storedCardToken));
        var both = storedBody(CUSTOMER_ID, storedCardToken);
        both.put("card", card(EXPIRY_YEAR));
        int bothSources = applyStatus(newIntent(), both);
        var saveAgain = storedBody(CUSTOMER_ID, storedCardToken);
        saveAgain.put("saveCard", true);
        int saveStored = applyStatus(newIntent(), saveAgain);

        assertAll(() -> assertEquals(List.of(400, 422, 422, 422), List.of(otherCustomer, noCustomer, bothSources, saveStored)),
            () -> assertEquals(1, cardApi.creates().size(), "只有通过 Payment 校验的请求到达 card"),
            () -> assertTrue(mapper.instrumentTokens().isEmpty()));
    }

    /** 已存卡的列出、删除与按 Customer 失效已迁到 card-service（/card/v1/stored-cards…），Payment 不再提供这些入口。 */
    @Test
    void test_deleteAndInvalidate() {
        int list = status(noRetryClient().get().uri("/payment-card/v1/stored-cards?clientId={c}&customerId={u}", CLIENT_ID,
            CUSTOMER_ID));
        int invalidate = status(noRetryClient().post().uri("/payment-card/v1/customers/{u}/stored-cards/invalidation",
            CUSTOMER_ID).contentType(MediaType.APPLICATION_JSON).body(Map.of("clientId", CLIENT_ID)));
        int reveal = status(noRetryClient().post().uri("/payment-card/v1/channel-card-materials/reveal")
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("paymentInstrumentToken", "pit")));

        assertAll(() -> assertEquals(List.of(404, 404, 404), List.of(list, invalidate, reveal)),
            () -> assertTrue(cardApi.creates().isEmpty()));
    }

    /** 原提交重放：同一已存卡返回原 Token；改成手输卡或改 Customer 时 card 返回 409，Payment 同样 409。 */
    @Test
    void test_replayStoredCard() {
        String storedCardToken = cardApi.storedCard(CLIENT_ID, CUSTOMER_ID, PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME);
        var intent = newIntent();
        String instrumentToken = apply(intent, storedBody(CUSTOMER_ID, storedCardToken));

        String replayed = apply(intent, storedBody(CUSTOMER_ID, storedCardToken));
        var manual = new HashMap<String, Object>(Map.of("card", card(EXPIRY_YEAR), "customerId", CUSTOMER_ID));
        int changedCard = applyStatus(intent, manual);

        assertAll(() -> assertEquals(instrumentToken, replayed),
            () -> assertEquals(409, changedCard), () -> assertEquals(1, mapper.instrumentTokens().size()));
    }

    /** 新建 Intent，用手输卡申请工具（可选关联 Customer 与同意保存）并成功支付，返回 pay 响应体。 */
    Map<String, Object> payManual(String clientId, String customerId, boolean saveCard, String expiryYear) {
        return payManual(clientId, customerId, saveCard, expiryYear, this::succeeded);
    }

    /** 同上，渠道按指定响应返回。 */
    Map<String, Object> payManual(String clientId, String customerId, boolean saveCard, String expiryYear,
        Runnable channelOutcome) {
        var intent = newIntent(clientId);
        var body = new HashMap<String, Object>(Map.of("card", card(expiryYear), "customerId", customerId));
        body.put("saveCard", saveCard);
        String instrumentToken = apply(intent, body);
        channelOutcome.run();
        return payment(intent, instrumentToken).pay().getBody();
    }

    /** 新建 Intent，用已存卡与新 CVV 申请工具并成功支付。 */
    Map<String, Object> payStored(String customerId, String storedCardToken) {
        var intent = newIntent();
        String instrumentToken = apply(intent, storedBody(customerId, storedCardToken));
        succeeded();
        return payment(intent, instrumentToken).pay().getBody();
    }

    /** 渠道成功结论，每次使用新的渠道交易号。 */
    void succeeded() {
        channelRefs++;
        channel.succeeded("T-STORED-" + channelRefs);
    }

    /** 渠道受理后拒绝。 */
    void declined() {
        channel.declined("T-STORED-D", "ISSUER_PAYMENT_REJECTED", "Transaction failed at issuer end.");
    }

    /** 已存卡申请体：Customer 可为空以验证必填。 */
    static Map<String, Object> storedBody(String customerId, String storedCardToken) {
        var body = new HashMap<String, Object>();
        body.put("storedCard", Map.of("storedCardToken", storedCardToken, "cvv", NEW_CVV));
        if (customerId != null) {
            body.put("customerId", customerId);
        }
        return body;
    }

    /** 手输卡输入。 */
    static Map<String, String> card(String expiryYear) {
        return Map.of("pan", PAN, "expiryMonth", EXPIRY_MONTH, "expiryYear", expiryYear, "cardholderName",
            CARDHOLDER_NAME, "cvv", CVV);
    }

    /** 申请工具，返回支付工具 Token（非 2xx 使测试失败）。 */
    String apply(Payment intent, Map<String, Object> extras) {
        return (String)client().post().uri("/payment-instrument/v1/card-tokens").contentType(MediaType.APPLICATION_JSON)
            .body(applyBody(intent, extras)).retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
            .getBody().get("paymentInstrumentToken");
    }

    /** 申请工具，返回 HTTP 状态（含 4xx/5xx）。 */
    int applyStatus(Payment intent, Map<String, Object> extras) {
        return status(noRetryClient().post().uri("/payment-instrument/v1/card-tokens").contentType(MediaType.APPLICATION_JSON)
            .body(applyBody(intent, extras)));
    }

    /** 申请体：Intent 身份加卡来源。 */
    static Map<String, Object> applyBody(Payment intent, Map<String, Object> extras) {
        var body = new HashMap<String, Object>(Map.of("clientId", intent.clientId, "intentId",
            String.valueOf(intent.intentId), "sourceSubmissionId", String.valueOf(intent.submissionId),
            "paymentMethodCode", PAYMENT_METHOD_CODE));
        body.putAll(extras);
        return body;
    }

    /** 执行请求并返回 HTTP 状态（含 4xx/5xx）。 */
    static int status(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** 不自动重发的客户端：默认 Apache HttpClient 收到 503 会自动重发。 */
    RestClient noRetryClient() {
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).baseUrl(baseUrl()).build();
    }
}
