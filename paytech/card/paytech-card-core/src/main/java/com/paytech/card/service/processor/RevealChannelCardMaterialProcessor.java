package com.paytech.card.service.processor;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.CardMaterialPurpose;
import com.paytech.card.core.dal.dataobject.CardMaterialTicket;
import com.paytech.card.domain.ChannelCardMaterial;
import com.paytech.card.domainservice.repository.CardMaterialTicketRepository;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.processor.request.RevealCardMaterialRequest;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 凭一次性票据读卡（design/05 §2.1，替代 Payment 的读卡回调）：channel-connector 执行支付类标准 API 时调用。
 *
 * <p>
 * 校验调用方 → 按票据摘要读票据（不存在 404）→ 已用、已过期、用途不符拒绝 → 单条条件更新标记已用（并发使用只有一方成功）→ 事务外交
 * {@link ClaimChannelCardMaterialProcessor} 经 Vault 取回 DEK 解密并 GETDEL 领取 CVV。票据先于领取标记已用：之后任何失败都不能凭同一票据再试，
 * CVV 未被领取时 Payment 可以申请新票据。任何拒绝都不返回卡数据。
 */
@Component
@AllArgsConstructor
public class RevealChannelCardMaterialProcessor
    extends AbstractProcessor<RevealCardMaterialRequest, ChannelCardMaterial> {
    private final Clock clock;
    private final CardCallerService cardCallerService;
    private final CardMaterialTicketRepository cardMaterialTicketRepository;
    private final ClaimChannelCardMaterialProcessor claimChannelCardMaterialProcessor;

    /** 只接受 channel-connector 与 Platform；其他调用方按资源不存在返回 404。 */
    @Override
    protected void validate(RevealCardMaterialRequest request) {
        // 第一段过渡，便于回退：外部 API 执行迁往 channel-connector 后仍接受 platform-support-service；第二段收回 platform-support-service。
        cardCallerService.require(request.getCaller(), CardApiConstants.CHANNEL_CONNECTOR_SERVICE,
            CardApiConstants.PLATFORM_SUPPORT_SERVICE);
    }

    @Override
    protected ChannelCardMaterial processRequest(RevealCardMaterialRequest request) {
        CardMaterialTicket ticket =
            cardMaterialTicketRepository.findByHash(IssueCardMaterialTicketProcessor.hash(request.getTicket()));
        if (ticket == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "读卡票据不存在");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        checkUsable(ticket, now);
        if (!cardMaterialTicketRepository.markUsed(ticket, now)) {
            throw new BizCheckFailException("读卡票据已使用或已过期: " + ticket.getCardMaterialTicketId());
        }
        return claimChannelCardMaterialProcessor.process(ticket.getInstrumentRef());
    }

    /** 未用、未过期且用途为渠道执行。 */
    static void checkUsable(CardMaterialTicket ticket, Instant now) {
        if (ticket.getUsedAt() != null) {
            throw new BizCheckFailException("读卡票据已使用: " + ticket.getCardMaterialTicketId());
        }
        if (!ticket.getExpiresAt().isAfter(now)) {
            throw new BizCheckFailException("读卡票据已过期: " + ticket.getCardMaterialTicketId());
        }
        if (!CardMaterialPurpose.CHANNEL_EXECUTION.getCode().equals(ticket.getPurpose())) {
            throw new BizCheckFailException("读卡票据用途不符: " + ticket.getCardMaterialTicketId());
        }
    }
}
