package com.paytech.card.service.processor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.core.dal.dataobject.CardMaterialTicket;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.domain.IssuedCardMaterialTicket;
import com.paytech.card.domainservice.repository.CardMaterialTicketRepository;
import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.card.integration.CardSecurityCodeClient;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.processor.request.IssueCardMaterialTicketRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 签发一次性读卡票据（design/05 §2.1）：Payment 已在自己一侧完成准入校验（Attempt 处理中、当前动作为执行中的 PAY / AUTHORIZE），
 * Card 只校验工具存在、未过期且 CVV 仍可领取。
 *
 * <p>
 * 校验调用方 → 读交易工具 → 期限与 CVV 检查（Redis 只判断存在，不读值）→ 生成随机 256 位票据，单条插入其 SHA-256 → 返回票据原值。
 * 票据截止时间取请求时长与工具期限中较早者。CVV 已被领取时拒绝签发：Payment 据此以 NOT_SENT 结束动作，与“CVV 不可重取”一致。
 */
@Component
@AllArgsConstructor
public class IssueCardMaterialTicketProcessor
    extends AbstractProcessor<IssueCardMaterialTicketRequest, IssuedCardMaterialTicket> {
    /** 票据最长有效期。 */
    static final Duration MAX_TTL = Duration.ofSeconds(90);

    private static final SecureRandom TICKET_RANDOM = new SecureRandom();

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final CardCallerService cardCallerService;
    private final CardSecurityCodeClient cardSecurityCodeClient;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;
    private final CardMaterialTicketRepository cardMaterialTicketRepository;

    /** 只接受 Payment；时长 1–90 秒。 */
    @Override
    protected void validate(IssueCardMaterialTicketRequest request) {
        cardCallerService.require(request.getCaller(), CardApiConstants.PAYMENT_SERVICE);
        if (request.getTtl().isNegative() || request.getTtl().isZero() || request.getTtl().compareTo(MAX_TTL) > 0) {
            throw new InvalidParameterException("票据有效时长必须在 1 至 90 秒之间");
        }
    }

    @Override
    protected IssuedCardMaterialTicket processRequest(IssueCardMaterialTicketRequest request) {
        PaymentCardInstrument instrument = paymentCardInstrumentRepository.findInstrument(request.getInstrumentRef());
        if (instrument == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "卡交易工具不存在");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!instrument.getExpiresAt().isAfter(now)) {
            throw new BizCheckFailException("卡交易工具已过期: " + instrument.getPaymentCardInstrumentId());
        }
        if (!cardSecurityCodeClient.claimable(instrument.getPaymentCardInstrumentId())) {
            throw new BizCheckFailException("卡安全码已领取或已过期，不能再签发读卡票据: "
                + instrument.getPaymentCardInstrumentId());
        }
        String ticket = newTicket();
        Instant expiresAt = earlier(now.plus(request.getTtl()), instrument.getExpiresAt());
        cardMaterialTicketRepository.insertTicket(row(request, ticket, expiresAt, now));
        return new IssuedCardMaterialTicket(ticket, expiresAt);
    }

    /** 票据摘要行：不含票据原值。 */
    CardMaterialTicket row(IssueCardMaterialTicketRequest request, String ticket, Instant expiresAt, Instant now) {
        CardMaterialTicket row = new CardMaterialTicket();
        row.setCardMaterialTicketId(idGenerator.nextId());
        row.setTicketHash(hash(ticket));
        row.setInstrumentRef(request.getInstrumentRef());
        row.setAttemptId(request.getAttemptId());
        row.setActionId(request.getActionId());
        row.setPurpose(request.getPurpose().getCode());
        row.setExpiresAt(expiresAt);
        row.setCaller(request.getCaller());
        row.setCreateTime(now);
        row.setUpdateTime(now);
        return row;
    }

    /** 两个时刻中较早者。 */
    static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    /** 32 字节随机数的 Base64URL（43 位），不含业务身份。 */
    static String newTicket() {
        byte[] bytes = new byte[32];
        TICKET_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 票据原值的 SHA-256 小写十六进制；签发与读卡共用
     *
     * @param ticket 票据原值
     * @return 64 位摘要
     */
    public static String hash(String ticket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ticket.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
