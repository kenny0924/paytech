package com.paytech.card.auto.ticket;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.ticket.CardMaterialTicketApi;
import com.paytech.card.integration.impl.CardSecurityCodeClientImpl;
import com.paytech.card.service.processor.IssueCardMaterialTicketProcessor;
import com.paytech.card.test.base.AbstractCardApiTest;

/**
 * 一次性读卡票据签发（design/05 §2.1）：从 card 的真实 HTTP 入口，经真实 Processor、mor_card 票据表与 Redis CVV 暂存。
 *
 * <p>
 * 覆盖：签发随机 256 位票据、只存摘要、期限不超过 90 秒与工具期限、不领取 CVV；CVV 已领取后拒绝签发；工具已过期拒绝；工具不存在 404；
 * 有效秒数越界 422；非 Payment 调用方 404。
 *
 * @see CardMaterialTicketApi
 */
class CardMaterialTicketTest extends AbstractCardApiTest {
    @Autowired
    private StringRedisTemplate redis;

    /** 签发：43 位 Base64URL 票据，库中只有摘要与绑定信息；截止时间不超过 90 秒；CVV 仍在。 */
    @Test
    void test_issueTicket() {
        String ref = newInstrument();

        var response = exchange(HttpMethod.POST, "/card/v1/material-tickets", CardApiConstants.PAYMENT_SERVICE,
            ticketBody(ref, 90)).getBody();

        String ticket = (String)response.get("ticket");
        var row = mapper.tickets().getFirst();
        Instant expiresAt = Instant.parse((String)response.get("expiresAt"));
        assertAll(() -> assertTrue(ticket.matches("[A-Za-z0-9_-]{43}"), "票据格式"),
            () -> assertEquals(IssueCardMaterialTicketProcessor.hash(ticket), row.get("ticketHash")),
            () -> assertNotEquals(ticket, row.get("ticketHash")),
            () -> assertEquals(List.of(ref, 880001L, 880002L, "CHANNEL_EXECUTION", CardApiConstants.PAYMENT_SERVICE),
                List.of(row.get("instrumentRef"), id(row, "attemptId"), id(row, "actionId"), row.get("purpose"), row.get("caller"))),
            () -> assertNull(row.get("usedAt")),
            () -> assertEquals(expiresAt, ((LocalDateTime)row.get("expiresAt")).toInstant(ZoneOffset.UTC)),
            () -> assertFalse(expiresAt.isAfter(Instant.now().plus(Duration.ofSeconds(91))), "票据期限超过 90 秒"),
            () -> assertTrue(redis.hasKey(CardSecurityCodeClientImpl.key(Long.parseLong(ref))), "签票不应领取 CVV"),
            () -> assertFalse(mapper.tickets().toString().contains(ticket), "库中出现票据原值"));
    }

    /** CVV 已被领取（读卡已完成）：拒绝签发新票据（400），Payment 据此以 NOT_SENT 结束动作。 */
    @Test
    void test_securityCodeClaimedRefused() {
        String ref = newInstrument();
        String ticket = issueTicket(ref);
        assertEquals(200, call(HttpMethod.POST, "/card/v1/channel-card-materials/reveal",
            CardApiConstants.PLATFORM_SUPPORT_SERVICE, java.util.Map.of("ticket", ticket)).status());

        int status = issueStatus(ref, 90);

        assertAll(() -> assertEquals(400, status), () -> assertEquals(1, mapper.tickets().size()));
    }

    /** 工具已过期：拒绝签发（400），不写票据。 */
    @Test
    void test_expiredInstrumentRefused() {
        String ref = newInstrument();
        mapper.expireInstrument(Long.parseLong(ref), 1);

        assertAll(() -> assertEquals(400, issueStatus(ref, 90)), () -> assertTrue(mapper.tickets().isEmpty()));
    }

    /** 工具不存在 404；有效秒数超过 90 或为 0 时请求校验 422；均不写票据。 */
    @Test
    void test_unknownInstrumentAndTtlBounds() {
        String ref = newInstrument();

        assertAll(() -> assertEquals(404, issueStatus("9000000000000000001", 90)),
            () -> assertEquals(422, issueStatus(ref, 91)), () -> assertEquals(422, issueStatus(ref, 0)),
            () -> assertTrue(mapper.tickets().isEmpty()));
    }

    /** 只接受 Payment：Platform 或 acquiring 申请票据为 404，不写票据。 */
    @Test
    void test_wrongCallerRejected() {
        String ref = newInstrument();

        var platform = call(HttpMethod.POST, "/card/v1/material-tickets", CardApiConstants.PLATFORM_SUPPORT_SERVICE,
            ticketBody(ref, 90));
        var acquiring = call(HttpMethod.POST, "/card/v1/material-tickets", CardApiConstants.ACQUIRING_SERVICE,
            ticketBody(ref, 90));

        assertAll(() -> assertEquals(List.of(404, 404), List.of(platform.status(), acquiring.status())),
            () -> assertTrue(mapper.tickets().isEmpty()));
    }

    /** 新建一个手输卡工具，返回工具引用。 */
    String newInstrument() {
        return (String)createInstrument(instrumentBody(submission(), card(PAN, EXPIRY_YEAR, CARDHOLDER_NAME), null, false))
            .get("instrumentRef");
    }

    /** 以 Payment 身份签发票据，返回 HTTP 状态。 */
    int issueStatus(String ref, int ttlSeconds) {
        return call(HttpMethod.POST, "/card/v1/material-tickets", CardApiConstants.PAYMENT_SERVICE,
            ticketBody(ref, ttlSeconds)).status();
    }
}
