package com.paytech.card.auto.event;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.card.service.handler.PaymentSucceededCardHandler;
import com.paytech.card.test.base.AbstractStoredCardTest;
import com.paytech.card.test.probe.PaymentEventPublisher;

/**
 * 支付成功事件化（design/05 §2.3）：测试扮演 Payment 向真实本机 RabbitMQ 的 payment.events 发布 PaymentSucceeded，经 card.payment.p0..p3、
 * paytech-messaging 幂等拦截器与 {@link PaymentSucceededCardHandler} 在 card 本地事务内标记凭证已验证并生成或更新已存卡。
 *
 * <p>
 * 覆盖：同意保存时生成一张已存卡且凭证已验证，同一事件重复投递只生成一张；未同意只验证凭证；同卡新凭证再次成功只更新已存卡；v1 事件与非卡支付确认跳过。
 */
class PaymentSucceededConsumeTest extends AbstractStoredCardTest {
    private static final String CUSTOMER_ID = "70001";

    /** 同意保存：凭证已验证、生成一张已存卡；同一事件（同一 eventId）重复投递只记一次消费、仍只有一张已存卡。 */
    @Test
    void test_duplicateDeliveryCreatesOneStoredCard() {
        String ref = (String)createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME),
            CUSTOMER_ID, true)).get("instrumentRef");
        long attemptId = attempt();

        publishAndAwait(attemptId, ref, true, CUSTOMER_ID);
        payment.publish(2, attemptId, 3, PaymentEventPublisher.payload(attemptId, ref, true, CUSTOMER_ID));
        await().atMost(CONSUME_WAIT).until(() -> payment.pending() == 0);

        var stored = mapper.storedCards();
        assertAll(() -> assertEquals(1, stored.size()), () -> assertEquals(1, mapper.consumedEventIds().size()),
            () -> assertEquals(Long.parseLong(CUSTOMER_ID), id(stored.getFirst(), "customerId")),
            () -> assertEquals(Long.parseLong(ref), id(stored.getFirst(), "sourceInstrumentId")),
            () -> assertTrue(((String)stored.getFirst().get("token")).matches("sc_[A-Za-z0-9_-]{28}"), "Token 格式"),
            () -> assertNotNull(mapper.cardCredentials().getFirst().get("verifiedTime"), "凭证未验证"));
    }

    /** 未同意保存：凭证照样验证，但不存卡。 */
    @Test
    void test_noConsentVerifiesOnly() {
        paySucceeded(CUSTOMER_ID, false, EXPIRY_YEAR);

        assertAll(() -> assertTrue(mapper.storedCards().isEmpty()),
            () -> assertNotNull(mapper.cardCredentials().getFirst().get("verifiedTime"), "凭证未验证"));
    }

    /** 同一 Customer 同卡换有效期再次成功：仍一张已存卡，Token 不变，指向新凭证。 */
    @Test
    void test_sameCardUpdatesCredential() {
        paySucceeded(CUSTOMER_ID, true, EXPIRY_YEAR);
        String token = (String)mapper.storedCards().getFirst().get("token");

        paySucceeded(CUSTOMER_ID, true, "2031");

        var stored = mapper.storedCards().getFirst();
        assertAll(() -> assertEquals(1, mapper.storedCards().size()), () -> assertEquals(token, stored.get("token")),
            () -> assertEquals(mapper.cardCredentials().get(1).get("credentialId"), stored.get("credentialId")));
    }

    /** Schema v1 事件（第 12 步前，卡表已在原支付事务内写入）与 v2 非卡支付（无 instrumentRef）：确认消费并跳过，不改卡表。 */
    @Test
    void test_v1AndNonCardEventsIgnored() {
        String ref = (String)createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME),
            CUSTOMER_ID, true)).get("instrumentRef");
        long v1Attempt = attempt();
        int before = mapper.consumedEventIds().size();
        var v1 = PaymentEventPublisher.payload(v1Attempt, ref, true, CUSTOMER_ID);
        v1.remove(List.of("instrumentRef", "consentToSave", "customerId"));
        payment.publish(1, v1Attempt, 2, v1);
        await().atMost(CONSUME_WAIT).until(() -> mapper.consumedEventIds().size() > before);

        publishAndAwait(attempt(), null, false, null);

        assertAll(() -> assertEquals(2, mapper.consumedEventIds().size()), () -> assertTrue(mapper.storedCards().isEmpty()),
            () -> assertNull(mapper.cardCredentials().getFirst().get("verifiedTime"), "跳过的事件不应验证凭证"));
    }
}
