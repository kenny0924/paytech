package com.paytech.card.auto.material;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.material.ChannelCardMaterialApi;
import com.paytech.card.integration.impl.CardSecurityCodeClientImpl;
import com.paytech.card.service.processor.IssueCardMaterialTicketProcessor;
import com.paytech.card.test.base.AbstractCardApiTest;

/**
 * 凭一次性票据读卡（design/05 §2.1，替代 Payment 读卡回调）：从 card 的真实 HTTP 入口，经真实 Processor、票据表、Vault 测试服务取回 DEK 解密与
 * Redis GETDEL 领取 CVV。
 *
 * <p>
 * 覆盖：成功一次并标记已用、CVV 领取后删除；第一段过渡期 channel-connector 与 Platform 两个调用方都能读卡；二次使用拒绝；过期拒绝；用途不符拒绝；
 * 其他调用方 404 且票据不被消耗；票据不存在 404。所有拒绝都不返回卡数据。
 *
 * @see ChannelCardMaterialApi
 */
class ChannelCardMaterialRevealTest extends AbstractCardApiTest {
    private static final String REVEAL = "/card/v1/channel-card-materials/reveal";

    @Autowired
    private StringRedisTemplate redis;

    /** 成功：返回与创建时一致的卡号、有效期、持卡人与 CVV；票据标记已用，CVV 从 Redis 删除，DEK 经 Vault 取回且不在事务内。 */
    @Test
    void test_revealOnce() {
        String ref = newInstrument();
        String ticket = issueTicket(ref);

        var card = exchange(HttpMethod.POST, REVEAL, CardApiConstants.CHANNEL_CONNECTOR_SERVICE, Map.of("ticket", ticket))
            .getBody();

        assertAll(() -> assertEquals(Map.of("pan", PAN, "expiryMonth", EXPIRY_MONTH, "expiryYear", EXPIRY_YEAR,
            "cardholderName", CARDHOLDER_NAME, "cvv", CVV), card),
            () -> assertNotNull(mapper.tickets().getFirst().get("usedAt"), "票据未标记已用"),
            () -> assertFalse(redis.hasKey(CardSecurityCodeClientImpl.key(Long.parseLong(ref))), "CVV 领取后仍存在"),
            () -> assertEquals(1, vault.cardKeyRevealCount()));
        transactions.assertOutsideTransactions();
    }

    /** 同一票据第二次使用：400，不返回卡数据，不再访问 Vault。 */
    @Test
    void test_secondUseRejected() {
        String ticket = issueTicket(newInstrument());
        reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);

        var again = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);

        assertAll(() -> assertEquals(400, again.status()), () -> assertNoCardData(again),
            () -> assertEquals(1, vault.cardKeyRevealCount()));
    }

    /** 票据已过期：400，不返回卡数据，CVV 不被领取。 */
    @Test
    void test_expiredTicketRejected() {
        String ref = newInstrument();
        String ticket = issueTicket(ref);
        mapper.expireTicket(IssueCardMaterialTicketProcessor.hash(ticket));

        var response = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);

        assertAll(() -> assertEquals(400, response.status()), () -> assertNoCardData(response),
            () -> assertTrue(redis.hasKey(CardSecurityCodeClientImpl.key(Long.parseLong(ref))), "过期票据领取了 CVV"),
            () -> assertEquals(0, vault.cardKeyRevealCount()));
    }

    /** 票据用途与读卡入口不符：400，不返回卡数据，票据不被标记已用。 */
    @Test
    void test_purposeMismatchRejected() {
        String ticket = issueTicket(newInstrument());
        mapper.changeTicketPurpose(IssueCardMaterialTicketProcessor.hash(ticket), "STORED_CARD_DISPLAY");

        var response = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);

        assertAll(() -> assertEquals(400, response.status()), () -> assertNoCardData(response),
            () -> assertNull(mapper.tickets().getFirst().get("usedAt")), () -> assertEquals(0, vault.cardKeyRevealCount()));
    }

    /**
     * 第一段过渡（统一对外渠道出口）：channel-connector 与 Platform 各凭自己的票据读卡，都返回创建时的卡数据并各自标记已用；
     * 第二段收回 Platform 后本场景只保留 channel-connector。
     */
    @Test
    void test_transitionCallersReveal() {
        String connectorTicket = issueTicket(newInstrument());
        String platformTicket = issueTicket(newInstrument());
        // 同卡号第二次建工具会复用已有卡并读取一次卡密钥，只计两次读卡新增的密钥读取
        int revealsBefore = vault.cardKeyRevealCount();

        var connector = exchange(HttpMethod.POST, REVEAL, CardApiConstants.CHANNEL_CONNECTOR_SERVICE,
            Map.of("ticket", connectorTicket)).getBody();
        var platform = exchange(HttpMethod.POST, REVEAL, CardApiConstants.PLATFORM_SUPPORT_SERVICE,
            Map.of("ticket", platformTicket)).getBody();

        Map<String, Object> expected = Map.of("pan", PAN, "expiryMonth", EXPIRY_MONTH, "expiryYear", EXPIRY_YEAR,
            "cardholderName", CARDHOLDER_NAME, "cvv", CVV);
        assertAll(() -> assertEquals(expected, connector), () -> assertEquals(expected, platform),
            () -> assertTrue(mapper.tickets().stream().allMatch(row -> row.get("usedAt") != null), "票据未全部标记已用"),
            () -> assertEquals(revealsBefore + 2, vault.cardKeyRevealCount()));
        transactions.assertOutsideTransactions();
    }

    /** 其他调用方（Payment、acquiring、counterparty、未声明）：404 且不返回卡数据、不消耗票据；之后 channel-connector 仍可凭该票据读卡一次。 */
    @Test
    void test_wrongCallerRejected() {
        String ticket = issueTicket(newInstrument());

        var payment = reveal(CardApiConstants.PAYMENT_SERVICE, ticket);
        var acquiring = reveal(CardApiConstants.ACQUIRING_SERVICE, ticket);
        var counterparty = reveal(CardApiConstants.COUNTERPARTY_SERVICE, ticket);
        var anonymous = reveal(null, ticket);
        var connector = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, ticket);

        assertAll(() -> assertEquals(List.of(404, 404, 404), List.of(payment.status(), acquiring.status(),
            counterparty.status())),
            () -> assertTrue(anonymous.status() >= 400 && anonymous.status() < 500, "未声明调用方应为 4xx"),
            () -> assertNoCardData(payment), () -> assertNoCardData(acquiring), () -> assertNoCardData(counterparty),
            () -> assertNoCardData(anonymous),
            () -> assertEquals(200, connector.status()), () -> assertEquals(1, vault.cardKeyRevealCount()));
    }

    /** 票据不存在：404，不返回卡数据。 */
    @Test
    void test_unknownTicketRejected() {
        newInstrument();

        var response = reveal(CardApiConstants.CHANNEL_CONNECTOR_SERVICE, "A".repeat(43));

        assertAll(() -> assertEquals(404, response.status()), () -> assertNoCardData(response));
    }

    /** 新建一个手输卡工具，返回工具引用。 */
    String newInstrument() {
        return (String)createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false))
            .get("instrumentRef");
    }

    /** 以给定调用方凭票据读卡。 */
    Response reveal(String caller, String ticket) {
        return call(HttpMethod.POST, REVEAL, caller, Map.of("ticket", ticket));
    }

    /** 响应中没有任何卡值。 */
    static void assertNoCardData(Response response) {
        assertAll(() -> assertFalse(response.body().contains(PAN), "拒绝响应包含卡号"),
            () -> assertFalse(response.body().contains(CARDHOLDER_NAME), "拒绝响应包含持卡人"),
            () -> assertFalse(response.body().contains("\"cvv\""), "拒绝响应包含 CVV"));
    }
}
