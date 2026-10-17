package com.paytech.payment.execution.service.processor;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.card.domain.ChannelCardMaterial;
import com.paytech.payment.card.service.processor.ClaimChannelCardMaterialProcessor;
import com.paytech.payment.execution.core.action.ActionType;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAction;
import com.paytech.payment.execution.core.dal.dataobject.PaymentAttempt;
import com.paytech.payment.execution.core.dal.dataobject.PaymentInstrumentToken;
import com.paytech.payment.execution.core.enums.ActionStatus;
import com.paytech.payment.execution.core.enums.AttemptStatus;
import com.paytech.payment.execution.domainservice.repository.PaymentActionRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentAttemptRepository;
import com.paytech.payment.execution.domainservice.repository.PaymentInstrumentTokenRepository;
import com.paytech.payment.execution.service.processor.request.RevealChannelCardMaterialRequest;

import lombok.AllArgsConstructor;

/**
 * 渠道执行读卡（卡数据经 Platform 渠道执行决定，2026-10-14）：Platform 执行支付类标准 API 时凭支付工具 Token 读取卡数据。
 *
 * <p>
 * Execution 核对 Token 与支付尝试的绑定，且该尝试当前正在执行 PAY 或 AUTHORIZE 资金动作；之后由 Card 领取 Processor 读库、经 Vault 取回本卡 DEK 解密，并一次性领取 Redis 中的 CVV。 不写数据库、不加 Intent
 * 锁（调用发生在 pay 持有 Intent 锁期间）；响应含卡原值，入口日志只记元数据。
 */
@Component
@AllArgsConstructor
public class RevealChannelCardMaterialProcessor
    extends AbstractProcessor<RevealChannelCardMaterialRequest, ChannelCardMaterial> {
    /** 唯一允许的调用方。 */
    static final String CALLER = "platform-support-service";

    final ClaimChannelCardMaterialProcessor claimChannelCardMaterialProcessor;
    final PaymentActionRepository paymentActionRepository;
    final PaymentAttemptRepository paymentAttemptRepository;
    final PaymentInstrumentTokenRepository paymentInstrumentTokenRepository;

    /** 调用方仅防误用；部署须限制为私网调用。 */
    @Override
    protected void validate(RevealChannelCardMaterialRequest request) {
        if (!CALLER.equals(request.getCaller())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付工具不存在");
        }
    }

    /** 核对绑定与当前资金动作后领取卡材料。 */
    @Override
    protected ChannelCardMaterial processRequest(RevealChannelCardMaterialRequest request) {
        PaymentInstrumentToken token =
            paymentInstrumentTokenRepository.findTokenByValue(request.getPaymentInstrumentToken());
        PaymentAttempt attempt = paymentAttemptRepository.selectById(request.getPaymentAttemptId());
        if (token == null || attempt == null || !token.getPaymentInstrumentToken().equals(request.getPaymentInstrumentToken())
            || !Objects.equals(attempt.getPaymentInstrumentTokenId(), token.getPaymentInstrumentTokenId())) {
            throw new FailException(CommonErrorCode.NOT_FOUND, "支付工具不存在");
        }
        if (token.getRevokedAt() != null || !isExecutingFundAction(attempt)) {
            throw new BizCheckFailException("支付尝试当前不允许用卡: " + attempt.getPaymentAttemptId());
        }
        return claimChannelCardMaterialProcessor.process(token.getInstrumentRef());
    }

    /** 尝试处理中，且当前动作是正在执行的 PAY 或 AUTHORIZE。 */
    boolean isExecutingFundAction(PaymentAttempt attempt) {
        if (attempt.getAttemptStatus() != AttemptStatus.PROCESSING || attempt.getActiveActionId() == null) {
            return false;
        }
        PaymentAction action = paymentActionRepository.selectById(attempt.getActiveActionId());
        return action != null && action.statusIn(ActionStatus.PROCESSING)
            && (action.getActionType() == ActionType.PAY || action.getActionType() == ActionType.AUTHORIZE);
    }
}
