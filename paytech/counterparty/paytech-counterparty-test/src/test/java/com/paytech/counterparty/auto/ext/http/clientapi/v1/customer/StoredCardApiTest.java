package com.paytech.counterparty.auto.ext.http.clientapi.v1.customer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.paytech.counterparty.api.client.v1.customer.ClientCustomerApi;
import com.paytech.counterparty.api.enums.CounterpartyErrorCode;
import com.paytech.counterparty.test.base.customer.AbstractCustomerApiTest;
import com.paytech.counterparty.test.mocker.external.StoredCardMocker.Call;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.card.api.storedcard.response.StoredCardListResponseBody;

/**
 * Client 已存卡接口（需求 20261015 commerce-Customer与存卡支付，AC-04、AC-11、AC-14）：经真实 Client API 入口与 Payment 测试 HTTP 服务。
 *
 * @see ClientCustomerApi
 */
class StoredCardApiTest extends AbstractCustomerApiTest {
    private static final String TOKEN = "sc_AAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    /** 列出：调用 Payment 时带 Client 与 Customer 主键，响应只含 Token、掩码与有效期、保存时间。 */
    @Test
    void test_list() {
        String customerNo = create("user-1").text("customerNo");
        String customerId = customerRows().getFirst().get("customerId").toString();
        storedCards.mockCards(List.of(card()));

        var response = listCards(clientId(), customerNo);

        var card = response.body().get("storedCards").get(0);
        assertAll(() -> assertEquals(200, response.status()),
            () -> assertEquals(List.of(new Call("LIST", Long.toString(clientId()), customerId, null)), storedCards.calls()),
            () -> assertEquals(TOKEN, card.get("storedCardToken").asString()),
            () -> assertEquals("411111******1111", card.get("maskedPan").asString()),
            () -> assertEquals("12", card.get("expiryMonth").asString()),
            () -> assertEquals("2030", card.get("expiryYear").asString()),
            () -> assertEquals(5, card.size(), "只返回 Token、掩码、有效期月年与保存时间"));
    }

    /** 已删除 Customer 返回空列表、不调用 Payment；删除其卡 404；跨 Client 404。 */
    @Test
    void test_deletedOrForeignCustomer() {
        String customerNo = create("user-1").text("customerNo");
        delete(clientId(), customerNo);
        await().atMost(Duration.ofSeconds(10)).until(() -> !storedCards.calls().isEmpty());
        int callsAfterDelete = storedCards.calls().size();

        var list = listCards(clientId(), customerNo);
        var deleteCard = deleteCard(clientId(), customerNo, TOKEN);
        var foreign = listCards(otherClientId(), customerNo);

        assertAll(() -> assertEquals(200, list.status()), () -> assertEquals(0, list.body().get("storedCards").size()),
            () -> assertEquals(404, deleteCard.status()),
            () -> assertEquals(CounterpartyErrorCode.STORED_CARD_NOT_FOUND.getCode(), deleteCard.text("code")),
            () -> assertEquals(404, foreign.status()),
            () -> assertEquals(CounterpartyErrorCode.CUSTOMER_NOT_FOUND.getCode(), foreign.text("code")),
            () -> assertEquals(callsAfterDelete, storedCards.calls().size(), "已删除或跨 Client 不应调用 Payment"));
    }

    /** 删除已存卡：Payment 成功 204；Payment 不存在 404；Payment 不可用 503。 */
    @Test
    void test_deleteCard() {
        String customerNo = create("user-1").text("customerNo");
        String customerId = customerRows().getFirst().get("customerId").toString();

        var deleted = deleteCard(clientId(), customerNo, TOKEN);
        storedCards.mockDeleteFound(false);
        var missing = deleteCard(clientId(), customerNo, TOKEN);
        storedCards.mockFailure(MockHttpFault.json(503, new ApiProblemResponse(ApiProblemProfile.TYPE,
            "Service Unavailable", "合成故障", "SERVICE_UNAVAILABLE", "card-test", "2026-09-08T00:00:00.000000Z", null)));
        var unavailable = deleteCard(clientId(), customerNo, TOKEN);

        assertAll(() -> assertEquals(List.of(204, 404, 503),
            List.of(deleted.status(), missing.status(), unavailable.status())),
            () -> assertEquals(new Call("DELETE", Long.toString(clientId()), customerId, TOKEN),
                storedCards.calls().getFirst()));
    }

    /** Payment 已存卡响应。 */
    static StoredCardListResponseBody.StoredCard card() {
        var card = new StoredCardListResponseBody.StoredCard();
        card.setStoredCardToken(TOKEN);
        card.setMaskedPan("411111******1111");
        card.setLast4("1111");
        card.setExpiryMonth("12");
        card.setExpiryYear("2030");
        card.setCreatedAt("2026-09-08T00:00:00Z");
        return card;
    }
}
