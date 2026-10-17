package com.paytech.commerce.auto.ext.http.customerapi.v1.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.commerce.customer.api.v1.checkout.CustomerCheckoutApi;
import com.paytech.commerce.customer.api.v1.enums.CustomerCheckoutErrorCode;
import com.paytech.commerce.customer.api.v1.payment.CustomerPaymentApi;
import com.paytech.commerce.customer.api.v1.payment.request.SubmitPaymentRequestBody;
import com.paytech.commerce.domain.checkout.CreateCheckoutContext;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.customer.PaymentResponseBuilder;
import com.paytech.commerce.test.builder.customer.SubmitPaymentRequestBuilder;
import com.paytech.commerce.test.checker.customer.CheckoutFlowResponseChecker;
import com.paytech.commerce.test.dal.CustomerTestMapper;
import com.paytech.commerce.test.mocker.external.CardInstrumentMocker;
import com.paytech.commerce.test.mocker.external.CheckoutCardMocker;
import com.paytech.commerce.test.mocker.external.CheckoutIntentMocker;
import com.paytech.commerce.test.mocker.external.StoredCardMocker;
import com.paytech.commerce.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.common.http.ApiHeaderNames;
import com.paytech.payment.api.card.response.StoredCardListResponseBody;
import com.paytech.payment.api.instrument.request.ApplyCardInstrumentRequestBody;

import tools.jackson.databind.JsonNode;

/**
 * Hosted Checkout 的存卡与已存卡支付（需求 20261015 commerce-Customer与存卡支付，AC-05、AC-09、AC-13）：经真实 Customer API 入口、
 * Processor、MySQL 与 Payment 测试 HTTP 服务（申请工具、pay、已存卡列表）。
 *
 * @see CustomerCheckoutApi#getCheckout(String, boolean)
 * @see CustomerPaymentApi#submitPayment(String, SubmitPaymentRequestBody)
 */
class StoredCardCheckoutTest extends MockTestBase {
    private static final long CUSTOMER_ID = 880_001L;
    private static final String CUSTOMER_NO = "cus_storedcardcheckouttest0000000001";
    private static final String TOKEN = "sc_AAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    CheckoutIntentMocker intents;
    @Autowired
    CardInstrumentMocker instruments;
    @Autowired
    CheckoutCardMocker cards;
    @Autowired
    StoredCardMocker storedCards;
    @Autowired
    CustomerTestMapper customers;

    private CreateCheckoutContext fixture;

    /** 每例准备可支付订单、Intent、卡工具与 pay 的响应 DTO。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = data.mockPrepared(true);
        intents.mockResult(PaymentResponseBuilder.builder().intentId(Long.toString(orderId())).build().intent());
        instruments.mockResponse(
            PaymentResponseBuilder.builder().expiresAt(adjustableUtcClock.instant().plusSeconds(60)).build().instrument());
        cards.mockResponse(CheckoutCardMocker.processing("123456"));
        storedCards.mockCards(List.of(storedCard()));
    }

    /** 有效 Customer 且已开通：可保存卡，并列出已存卡（只含 Token、后四位与有效期）。 */
    @Test
    void test_getOffersStoredCards() {
        attachCustomer(true);

        JsonNode body = read(getCheckout());

        var card = body.get("storedCards").get(0);
        assertAll(() -> assertTrue(body.get("cardSaving").get("available").asBoolean()),
            () -> assertEquals(TOKEN, card.get("storedCardToken").asString()),
            () -> assertEquals("1111", card.get("last4").asString()), () -> assertEquals(4, card.size()),
            () -> assertEquals(List.of(new StoredCardMocker.Call("LIST", Long.toString(clientId()),
                Long.toString(CUSTOMER_ID), null)), storedCards.calls()));
    }

    /** 未开通或访客订单：不可保存卡、已存卡为空，且不调用 Payment 已存卡接口。 */
    @Test
    void test_getWithoutEligibility() {
        JsonNode guest = read(getCheckout());
        attachCustomer(false);
        JsonNode disabled = read(getCheckout());

        assertAll(() -> assertFalse(guest.get("cardSaving").get("available").asBoolean()),
            () -> assertEquals(0, guest.get("storedCards").size()),
            () -> assertFalse(disabled.get("cardSaving").get("available").asBoolean()),
            () -> assertEquals(0, disabled.get("storedCards").size()), () -> assertTrue(storedCards.calls().isEmpty()));
    }

    /** 手输卡并同意保存：申请工具带 Customer 与 saveCard，pay 的付款人引用为公开 Customer 编号。 */
    @Test
    void test_submitSaveCard() {
        attachCustomer(true);
        instruments.observe(request -> assertAll(() -> assertEquals(Long.toString(CUSTOMER_ID), request.getCustomerId()),
            () -> assertEquals(Boolean.TRUE, request.getSaveCard()), () -> assertNotNull(request.getCard()),
            () -> assertNull(request.getStoredCard())));
        var body = SubmitPaymentRequestBuilder.forContract(orderId());
        body.setSaveCard(true);

        submit(body).assertProcessing();

        assertAll(() -> assertEquals(1, instruments.calls().size()),
            () -> assertEquals(CUSTOMER_NO, cards.calls().getFirst().consumerReference()),
            () -> assertEquals("AUTOMATIC", cards.calls().getFirst().captureMode()));
    }

    /** 已存卡加 CVV：申请工具只带已存卡与 Customer，不带卡信息。 */
    @Test
    void test_submitStoredCard() {
        attachCustomer(true);
        instruments.observe(request -> {
            ApplyCardInstrumentRequestBody.StoredCard stored = request.getStoredCard();
            assertAll(() -> assertNull(request.getCard()), () -> assertEquals(TOKEN, stored.getStoredCardToken()),
                () -> assertEquals("4321", stored.getCvv()),
                () -> assertEquals(Long.toString(CUSTOMER_ID), request.getCustomerId()),
                () -> assertNull(request.getSaveCard()));
        });

        submit(storedCardBody()).assertProcessing();

        assertEquals(1, instruments.calls().size());
    }

    /** 未开通时选择保存或使用已存卡：422 STORED_CARD_NOT_AVAILABLE，不申请工具、不支付。 */
    @Test
    void test_storedCardNotAvailable() {
        attachCustomer(false);
        var save = SubmitPaymentRequestBuilder.forContract(orderId());
        save.setSaveCard(true);

        var saveResponse = submit(save);
        var storedResponse = submit(storedCardBody());

        saveResponse.assertProblem(422, CustomerCheckoutErrorCode.STORED_CARD_NOT_AVAILABLE.getCode());
        storedResponse.assertProblem(422, CustomerCheckoutErrorCode.STORED_CARD_NOT_AVAILABLE.getCode());
        assertAll(() -> assertTrue(instruments.calls().isEmpty()), () -> assertTrue(cards.calls().isEmpty()));
    }

    /** 访客订单：不传 Customer，pay 的付款人引用为订单号。 */
    @Test
    void test_guestPayerReference() {
        instruments.observe(request -> assertAll(() -> assertNull(request.getCustomerId()),
            () -> assertNull(request.getSaveCard())));

        submit(SubmitPaymentRequestBuilder.forContract(orderId())).assertProcessing();

        assertEquals(fixture.getOrder().getOrderNo(), cards.calls().getFirst().consumerReference());
    }

    /** 插入有效 Customer、关联订单，并设置 Client 存卡开通。 */
    void attachCustomer(boolean enabled) {
        customers.insertCustomer(CUSTOMER_ID, CUSTOMER_NO, clientId(), "user-checkout");
        customers.linkOrder(orderId(), CUSTOMER_ID);
        customers.setStoredCardEnabled(clientId(), enabled);
    }

    /** 已存卡加本次 CVV 的提交体。 */
    SubmitPaymentRequestBody storedCardBody() {
        var body = SubmitPaymentRequestBuilder.forContract(orderId());
        body.setCard(null);
        var stored = new SubmitPaymentRequestBody.StoredCardInfo();
        stored.setStoredCardToken(TOKEN);
        stored.setCvv("4321");
        body.setStoredCard(stored);
        return body;
    }

    /** Payment 已存卡响应。 */
    static StoredCardListResponseBody.StoredCard storedCard() {
        var card = new StoredCardListResponseBody.StoredCard();
        card.setStoredCardToken(TOKEN);
        card.setMaskedPan("411111******1111");
        card.setLast4("1111");
        card.setExpiryMonth("12");
        card.setExpiryYear("2030");
        card.setCreatedAt("2026-09-08T00:00:00Z");
        return card;
    }

    long clientId() {
        return fixture.getOrder().getClientId();
    }

    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    String path() {
        return "/customer-gateway-api/v1/checkouts/" + fixture.getSession().getPublicToken();
    }

    HttpHeaders headers() {
        var headers = new HttpHeaders();
        headers.set(ApiHeaderNames.REQUEST_ID, REQUEST_ID);
        return headers;
    }

    EntityExchangeResult<byte[]> getCheckout() {
        var result = httpTestInvoker.get(path(), headers());
        assertEquals(200, result.getStatus().value());
        return result;
    }

    JsonNode read(EntityExchangeResult<byte[]> result) {
        return objectMapper.readTree(result.getResponseBody());
    }

    CheckoutFlowResponseChecker submit(SubmitPaymentRequestBody body) {
        return new CheckoutFlowResponseChecker(httpTestInvoker.post(path() + "/payments", headers(), body), objectMapper,
            REQUEST_ID);
    }
}
