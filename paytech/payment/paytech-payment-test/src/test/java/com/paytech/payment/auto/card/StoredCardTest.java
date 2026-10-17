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

import com.paytech.payment.api.card.StoredCardApi;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.test.base.AbstractPayCardTest;
import com.paytech.payment.test.mocker.PlatformMocker;

/**
 * 已存卡（需求 20261015 commerce-Customer与存卡支付，技术方案 §2.4）：经工具申请、pay 与已存卡私网接口的真实 HTTP 入口，
 * 走真实 Card Processor、支付成功事务与 MySQL/Redis，Vault 为测试 HTTP 服务。
 *
 * <p>
 * 覆盖：同意保存且支付成功才生成已存卡并标记凭证已验证；未同意、支付失败不存；同卡再存只更新凭证、Token 不变；不同 Client 的 Token 不同；
 * 用已存卡加新 CVV 支付时渠道读到保存的卡值；归属、状态与参数校验；删除与按 Customer 失效；重放不可换卡。卡号均为公开测试卡号。
 *
 * @see StoredCardApi
 */
class StoredCardTest extends AbstractPayCardTest {
    private static final String CUSTOMER_ID = "70001";
    private static final String OTHER_CUSTOMER_ID = "70002";
    private static final String OTHER_CLIENT_ID = "102";
    private static final String NEW_CVV = "4321";
    private static final String DECLINED = """
        {"paymentStatus":"FAILED","channelTransactionRef":"T-STORED-D","channelStatus":"FAILED",
         "failureCode":"ISSUER_PAYMENT_REJECTED","failureMessage":"Transaction failed at issuer end."}""";

    /** 每笔渠道交易号不同；渠道交易引用按交易号唯一。 */
    private int channelRefs;

    /** 本类公共：卡直付渠道、风控放行；渠道响应在每笔支付前配置。 */
    @BeforeEach
    void setUpClassMocks() {
        data.cardFixture("PAYERMAX", ENTITY_NO, CURRENCY);
        risk.respond(RiskResult.ALLOW, AuthenticationRequirement.NOT_REQUIRED);
    }

    /** 同意保存且支付成功：凭证已验证，生成一张已存卡；列表只返回 Token、前 6 后 4 与有效期。 */
    @Test
    void test_saveCardOnSuccess() {
        var paid = payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);

        var stored = mapper.storedCards().getFirst();
        var listed = list(CLIENT_ID, CUSTOMER_ID).getFirst();
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), paid.get("status")),
            () -> assertEquals(1, mapper.storedCards().size()),
            () -> assertNotNull(mapper.cardCredentials().getFirst().get("verifiedTime"), "凭证未验证"),
            () -> assertTrue(((String)stored.get("token")).matches("sc_[A-Za-z0-9_-]{28}"), "Token 格式"),
            () -> assertEquals(Long.parseLong(CUSTOMER_ID), ((Number)stored.get("customerId")).longValue()),
            () -> assertEquals(stored.get("token"), listed.get("storedCardToken")),
            () -> assertEquals("411111******1111", listed.get("maskedPan")), () -> assertEquals("1111", listed.get("last4")),
            () -> assertEquals(EXPIRY_MONTH, listed.get("expiryMonth")), () -> assertEquals(EXPIRY_YEAR, listed.get("expiryYear")),
            () -> assertEquals(6, listed.size(), "列表字段只有 Token、掩码、后四位、有效期与创建时间"));
    }

    /** 未同意保存：凭证照样验证，但不存卡；支付失败：不存卡、凭证不验证。 */
    @Test
    void test_noSaveWithoutConsentOrSuccess() {
        payManual(CLIENT_ID, CUSTOMER_ID, false, EXPIRY_YEAR);
        boolean verifiedWithoutSave = mapper.cardCredentials().getFirst().get("verifiedTime") != null;

        var declined = payManual(CLIENT_ID, OTHER_CUSTOMER_ID, true, "2031", DECLINED);

        assertAll(() -> assertTrue(verifiedWithoutSave, "未同意保存时凭证也应在成功后验证"),
            () -> assertEquals(AttemptStatus.FAILED.getCode(), declined.get("status")),
            () -> assertTrue(mapper.storedCards().isEmpty()),
            () -> assertNull(mapper.cardCredentials().get(1).get("verifiedTime"), "失败的凭证被标记为已验证"));
    }

    /** 同一 Customer 同卡换有效期再存：仍一张已存卡，Token 不变，指向新凭证。 */
    @Test
    void test_saveSameCardUpdatesCredential() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        payManual(CLIENT_ID, CUSTOMER_ID, true, "2031");

        var stored = mapper.storedCards().getFirst();
        assertAll(() -> assertEquals(1, mapper.storedCards().size()), () -> assertEquals(token, stored.get("token")),
            () -> assertEquals(mapper.cardCredentials().get(1).get("credentialId"), stored.get("credentialId")),
            () -> assertEquals("2031", list(CLIENT_ID, CUSTOMER_ID).getFirst().get("expiryYear")));
    }

    /** 同一张卡在两个 Client 下保存：两张已存卡，Token 不同，各自只能看到自己的。 */
    @Test
    void test_tokenPerClient() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        payManual(OTHER_CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);

        var stored = mapper.storedCards();
        assertAll(() -> assertEquals(2, stored.size()),
            () -> assertNotEquals(stored.get(0).get("token"), stored.get(1).get("token")),
            () -> assertEquals(stored.get(0).get("token"), list(CLIENT_ID, CUSTOMER_ID).getFirst().get("storedCardToken")),
            () -> assertEquals(stored.get(1).get("token"),
                list(OTHER_CLIENT_ID, CUSTOMER_ID).getFirst().get("storedCardToken")));
    }

    /** 用已存卡与新 CVV 支付：不新增卡与凭证，渠道读到保存的卡值与本次 CVV。 */
    @Test
    void test_payWithStoredCard() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");
        platform.clear();

        var paid = payStored(CUSTOMER_ID, token);

        var instrument = mapper.cardInstruments().get(1);
        assertAll(() -> assertEquals(AttemptStatus.SUCCEEDED.getCode(), paid.get("status")),
            () -> assertEquals(1, mapper.cards().size()), () -> assertEquals(1, mapper.cardCredentials().size()),
            () -> assertEquals(mapper.storedCards().getFirst().get("storedCardId"), instrument.get("storedCardId")),
            () -> assertEquals(PlatformMocker.materialDigest(PAN, EXPIRY_MONTH, EXPIRY_YEAR, CARDHOLDER_NAME, NEW_CVV),
                platform.only().materialDigest(), "渠道读到的卡值与已存卡或本次 CVV 不一致"),
            () -> assertEquals(1, mapper.storedCards().size()));
    }

    /** 已存卡不属于该 Customer、已删除：业务拒绝 400；缺 Customer、同时给卡与已存卡、已存卡再选保存：请求校验 422。均不写工具。 */
    @Test
    void test_storedCardRejected() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");
        int instruments = mapper.cardInstruments().size();

        int otherCustomer = applyStatus(newIntent(), storedBody(OTHER_CUSTOMER_ID, token));
        int noCustomer = applyStatus(newIntent(), storedBody(null, token));
        var both = storedBody(CUSTOMER_ID, token);
        both.put("card", card(EXPIRY_YEAR));
        int bothSources = applyStatus(newIntent(), both);
        var saveAgain = storedBody(CUSTOMER_ID, token);
        saveAgain.put("saveCard", true);
        int saveStored = applyStatus(newIntent(), saveAgain);
        delete(CLIENT_ID, CUSTOMER_ID, token);
        int deleted = applyStatus(newIntent(), storedBody(CUSTOMER_ID, token));

        assertAll(() -> assertEquals(List.of(400, 422, 422, 422, 400),
            List.of(otherCustomer, noCustomer, bothSources, saveStored, deleted)),
            () -> assertEquals(instruments, mapper.cardInstruments().size()));
    }

    /** 删除与按 Customer 失效：列表变空；跨 Customer 删除 404；重复删除与重复失效幂等。 */
    @Test
    void test_deleteAndInvalidate() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        payManual(CLIENT_ID, OTHER_CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        int crossCustomer = delete(CLIENT_ID, OTHER_CUSTOMER_ID, token);
        int first = delete(CLIENT_ID, CUSTOMER_ID, token);
        int again = delete(CLIENT_ID, CUSTOMER_ID, token);
        int invalidated = invalidate(CLIENT_ID, OTHER_CUSTOMER_ID);
        int invalidatedAgain = invalidate(CLIENT_ID, OTHER_CUSTOMER_ID);

        assertAll(() -> assertEquals(List.of(404, 204, 204), List.of(crossCustomer, first, again)),
            () -> assertEquals(List.of(1, 0), List.of(invalidated, invalidatedAgain)),
            () -> assertTrue(list(CLIENT_ID, CUSTOMER_ID).isEmpty()),
            () -> assertTrue(list(CLIENT_ID, OTHER_CUSTOMER_ID).isEmpty()),
            () -> assertTrue(mapper.storedCards().stream().allMatch(row -> "DELETED".equals(row.get("status")))));
    }

    /** 原提交重放：同一已存卡返回原 Token；改成手输卡或改 Customer 返回 409。 */
    @Test
    void test_replayStoredCard() {
        payManual(CLIENT_ID, CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");
        var intent = newIntent();
        String instrumentToken = apply(intent, storedBody(CUSTOMER_ID, token));

        String replayed = apply(intent, storedBody(CUSTOMER_ID, token));
        var manual = new HashMap<String, Object>(Map.of("card", card(EXPIRY_YEAR), "customerId", CUSTOMER_ID));
        int changedCard = applyStatus(intent, manual);
        int changedCustomer = applyStatus(intent, storedBody(OTHER_CUSTOMER_ID, token));

        assertAll(() -> assertEquals(instrumentToken, replayed),
            () -> assertEquals(List.of(409, 409), List.of(changedCard, changedCustomer)));
    }

    /** 新建 Intent，用手输卡申请工具（可选关联 Customer 与同意保存）并成功支付，返回 pay 响应体。 */
    Map<String, Object> payManual(String clientId, String customerId, boolean saveCard, String expiryYear) {
        return payManual(clientId, customerId, saveCard, expiryYear, succeeded());
    }

    /** 同上，渠道按指定响应返回。 */
    Map<String, Object> payManual(String clientId, String customerId, boolean saveCard, String expiryYear,
        String channelResponse) {
        var intent = newIntent(clientId);
        var body = new HashMap<String, Object>(Map.of("card", card(expiryYear), "customerId", customerId));
        body.put("saveCard", saveCard);
        String instrumentToken = apply(intent, body);
        platform.respond("SUCCESS", channelResponse);
        return payment(intent, instrumentToken).pay().getBody();
    }

    /** 新建 Intent，用已存卡与新 CVV 申请工具并成功支付。 */
    Map<String, Object> payStored(String customerId, String storedCardToken) {
        var intent = newIntent();
        String instrumentToken = apply(intent, storedBody(customerId, storedCardToken));
        platform.respond("SUCCESS", succeeded());
        return payment(intent, instrumentToken).pay().getBody();
    }

    /** 成功响应，每次使用新的渠道交易号。 */
    String succeeded() {
        channelRefs++;
        return """
            {"paymentStatus":"SUCCEEDED","channelTransactionRef":"T-STORED-%d","channelStatus":"SUCCESS"}"""
            .formatted(channelRefs);
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
        try {
            return noRetryClient().post().uri("/payment-instrument/v1/card-tokens").contentType(MediaType.APPLICATION_JSON)
                .body(applyBody(intent, extras)).retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** 申请体：Intent 身份加卡来源。 */
    static Map<String, Object> applyBody(Payment intent, Map<String, Object> extras) {
        var body = new HashMap<String, Object>(Map.of("clientId", intent.clientId, "intentId",
            String.valueOf(intent.intentId), "sourceSubmissionId", String.valueOf(intent.submissionId),
            "paymentMethodCode", PAYMENT_METHOD_CODE));
        body.putAll(extras);
        return body;
    }

    /** 列出已存卡。 */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list(String clientId, String customerId) {
        var body = client().get().uri("/payment-card/v1/stored-cards?clientId={c}&customerId={u}", clientId, customerId)
            .retrieve().toEntity(new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        return (List<Map<String, Object>>)body.get("storedCards");
    }

    /** 删除已存卡，返回 HTTP 状态。 */
    int delete(String clientId, String customerId, String storedCardToken) {
        try {
            return noRetryClient().delete()
                .uri("/payment-card/v1/stored-cards/{t}?clientId={c}&customerId={u}", storedCardToken, clientId,
                    customerId)
                .retrieve().toBodilessEntity().getStatusCode().value();
        } catch (RestClientResponseException failure) {
            return failure.getStatusCode().value();
        }
    }

    /** 按 Customer 失效已存卡，返回失效条数。 */
    int invalidate(String clientId, String customerId) {
        var body = client().post().uri("/payment-card/v1/customers/{u}/stored-cards/invalidation", customerId)
            .contentType(MediaType.APPLICATION_JSON).body(Map.of("clientId", clientId)).retrieve()
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
        return ((Number)body.get("invalidatedCount")).intValue();
    }

    /** 不自动重发的客户端：默认 Apache HttpClient 收到 503 会自动重发。 */
    RestClient noRetryClient() {
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).baseUrl(baseUrl()).build();
    }
}
