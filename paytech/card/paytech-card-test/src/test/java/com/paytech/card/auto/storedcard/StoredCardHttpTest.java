package com.paytech.card.auto.storedcard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.storedcard.StoredCardApi;
import com.paytech.card.test.base.AbstractStoredCardTest;

/**
 * 已存卡私网接口（自 Payment 迁入，design/05 §3）：已存卡由真实 PaymentSucceeded 消费链路生成，再经 card 的真实 HTTP 入口列出、删除、按 Customer 失效，
 * 以及以已存卡加新 CVV 创建工具并凭票据读卡。
 *
 * <p>
 * 覆盖：列表只有 Token、前 6 后 4、有效期与创建时间；跨 Customer 删除 404、重复删除幂等；失效可重复；调用方白名单（列出 acquiring / consumer，
 * 删除与失效只 consumer）；已存卡支付不新增卡与凭证，读卡得到保存的卡值与本次 CVV；已存卡不属于该 Customer 或已删除为 400。
 *
 * @see StoredCardApi
 */
class StoredCardHttpTest extends AbstractStoredCardTest {
    private static final String CUSTOMER_ID = "70001";
    private static final String OTHER_CUSTOMER_ID = "70002";
    private static final String NEW_CVV = "4321";

    /** 列出：acquiring 与 consumer 均可，只返回 Token、掩码卡号、后四位、有效期与创建时间。 */
    @Test
    void test_listStoredCards() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);

        var byAcquiring = list(CardApiConstants.ACQUIRING_SERVICE, CUSTOMER_ID);
        var byConsumer = list(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID);

        var listed = byAcquiring.getFirst();
        assertAll(() -> assertEquals(byAcquiring, byConsumer), () -> assertEquals(1, byAcquiring.size()),
            () -> assertEquals(mapper.storedCards().getFirst().get("token"), listed.get("storedCardToken")),
            () -> assertEquals("411111******1111", listed.get("maskedPan")), () -> assertEquals("1111", listed.get("last4")),
            () -> assertEquals(EXPIRY_MONTH, listed.get("expiryMonth")), () -> assertEquals(EXPIRY_YEAR, listed.get("expiryYear")),
            () -> assertEquals(6, listed.size(), "列表字段只有 Token、掩码、后四位、有效期与创建时间"),
            () -> assertTrue(list(CardApiConstants.CONSUMER_SERVICE, OTHER_CUSTOMER_ID).isEmpty()));
    }

    /** 删除：跨 Customer 404；首次与重复删除 204；删除后列表为空。 */
    @Test
    void test_deleteStoredCard() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        int crossCustomer = delete(CardApiConstants.CONSUMER_SERVICE, OTHER_CUSTOMER_ID, token);
        int first = delete(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID, token);
        int again = delete(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID, token);

        assertAll(() -> assertEquals(List.of(404, 204, 204), List.of(crossCustomer, first, again)),
            () -> assertTrue(list(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID).isEmpty()),
            () -> assertEquals("DELETED", mapper.storedCards().getFirst().get("status")));
    }

    /** 按 Customer 失效：第一次失效 1 条，重复调用 0 条，列表为空。 */
    @Test
    void test_invalidateCustomer() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);

        int first = invalidate(CUSTOMER_ID);
        int again = invalidate(CUSTOMER_ID);

        assertAll(() -> assertEquals(List.of(1, 0), List.of(first, again)),
            () -> assertTrue(list(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID).isEmpty()));
    }

    /** 调用方白名单：acquiring 删除与失效、Payment 与 Platform 列出均 404，已存卡不受影响。 */
    @Test
    void test_callerRules() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        int acquiringDelete = delete(CardApiConstants.ACQUIRING_SERVICE, CUSTOMER_ID, token);
        int acquiringInvalidate = call(HttpMethod.POST, "/card/v1/customers/" + CUSTOMER_ID + "/stored-cards/invalidation",
            CardApiConstants.ACQUIRING_SERVICE, Map.of("clientId", CLIENT_ID)).status();
        int paymentList = listStatus(CardApiConstants.PAYMENT_SERVICE);
        int platformList = listStatus(CardApiConstants.PLATFORM_SUPPORT_SERVICE);

        assertAll(() -> assertEquals(List.of(404, 404, 404, 404),
            List.of(acquiringDelete, acquiringInvalidate, paymentList, platformList)),
            () -> assertEquals("ACTIVE", mapper.storedCards().getFirst().get("status")));
    }

    /** 已存卡加新 CVV 创建工具：不新增卡与凭证，工具指向已存卡；凭票据读到保存的卡值与本次 CVV。 */
    @Test
    void test_payWithStoredCard() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        String ref = (String)createInstrument(storedBody(submission(), token, CUSTOMER_ID, NEW_CVV)).get("instrumentRef");
        var card = exchange(HttpMethod.POST, "/card/v1/channel-card-materials/reveal",
            CardApiConstants.PLATFORM_SUPPORT_SERVICE, Map.of("ticket", issueTicket(ref))).getBody();

        var instrument = mapper.cardInstruments().get(1);
        assertAll(() -> assertEquals(1, mapper.cards().size()), () -> assertEquals(1, mapper.cardCredentials().size()),
            () -> assertEquals(mapper.storedCards().getFirst().get("storedCardId"), instrument.get("storedCardId")),
            () -> assertEquals(Map.of("pan", PAN, "expiryMonth", EXPIRY_MONTH, "expiryYear", EXPIRY_YEAR, "cardholderName",
                CARDHOLDER_NAME, "cvv", NEW_CVV), card));
    }

    /** 已存卡不属于该 Customer、已删除：400，不新增工具；用已存卡同时同意保存：400。 */
    @Test
    void test_storedCardRejected() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");
        int instruments = mapper.cardInstruments().size();

        int otherCustomer = create(storedBody(submission(), token, OTHER_CUSTOMER_ID, NEW_CVV));
        var saveAgain = storedBody(submission(), token, CUSTOMER_ID, NEW_CVV);
        saveAgain.put("consentToSave", true);
        int saveStored = create(saveAgain);
        delete(CardApiConstants.CONSUMER_SERVICE, CUSTOMER_ID, token);
        int deleted = create(storedBody(submission(), token, CUSTOMER_ID, NEW_CVV));

        assertAll(() -> assertEquals(List.of(400, 400, 400), List.of(otherCustomer, saveStored, deleted)),
            () -> assertEquals(instruments, mapper.cardInstruments().size()));
    }

    /** 列出已存卡。 */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> list(String caller, String customerId) {
        var body = exchange(HttpMethod.GET, "/card/v1/stored-cards?clientId=" + CLIENT_ID + "&customerId=" + customerId,
            caller, null).getBody();
        return (List<Map<String, Object>>)body.get("storedCards");
    }

    /** 以给定调用方列出，返回 HTTP 状态。 */
    int listStatus(String caller) {
        return call(HttpMethod.GET, "/card/v1/stored-cards?clientId=" + CLIENT_ID + "&customerId=" + CUSTOMER_ID, caller,
            null).status();
    }

    /** 删除已存卡，返回 HTTP 状态。 */
    int delete(String caller, String customerId, String token) {
        return call(HttpMethod.DELETE, "/card/v1/stored-cards/" + token + "?clientId=" + CLIENT_ID + "&customerId="
            + customerId, caller, null).status();
    }

    /** 按 Customer 失效，返回失效条数。 */
    int invalidate(String customerId) {
        var body = exchange(HttpMethod.POST, "/card/v1/customers/" + customerId + "/stored-cards/invalidation",
            CardApiConstants.CONSUMER_SERVICE, Map.of("clientId", CLIENT_ID)).getBody();
        return ((Number)body.get("invalidatedCount")).intValue();
    }

    /** 以 Payment 身份创建工具，返回 HTTP 状态。 */
    int create(Map<String, Object> body) {
        return call(HttpMethod.POST, "/card/v1/instruments", CardApiConstants.PAYMENT_SERVICE, body).status();
    }
}
