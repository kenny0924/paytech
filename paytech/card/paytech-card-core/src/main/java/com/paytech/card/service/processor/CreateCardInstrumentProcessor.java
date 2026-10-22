package com.paytech.card.service.processor;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.core.dal.dataobject.PaymentCard;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.domain.CardInstrumentContext;
import com.paytech.card.domain.CardInstrumentRecord;
import com.paytech.card.domain.CardRiskAttributes;
import com.paytech.card.domain.CreatedCardInstrument;
import com.paytech.card.domain.binlibrary.CardAttributes;
import com.paytech.card.domainservice.repository.CardBinRangeRepository;
import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;
import com.paytech.card.service.CardCallerService;
import com.paytech.card.service.CardService;
import com.paytech.card.service.CardStorageService;
import com.paytech.card.service.processor.request.CreateCardInstrumentRequest;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.processor.AbstractProcessor;

import lombok.AllArgsConstructor;

/**
 * 创建卡交易工具（design/05 §2.2 两段创建的第一段）：原 {@code PrepareCardInstrumentProcessor} 的事务外准备与 {@code applyInstrument} 合并为
 * Card 的一个命令，Payment 随后在自己的本地事务内保存支付工具 Token。
 *
 * <p>
 * 按（Client、来源提交）查原工具 → 已有则由 {@link CheckCardInstrumentProcessor} 核对同卡、同 Customer、同保存意愿后原样返回（不访问 Vault、
 * 不覆盖 CVV）→ 没有则准备卡、凭证、DEK 与 CVV 并插入交易工具；并发同一提交撞唯一键时改读赢得竞争的工具并同样核对。Payment 第二段失败留下的
 * 孤立工具从未签发票据，到期后由 {@link PurgeOrphanCardInstrumentsProcessor} 回收；其 CVV 随工具期限过期。
 * <li>响应附带按当前生效 BIN 库查询的卡属性快照（BIN 库需求 B4）：新建与原提交重放都查询一次，Payment 只在首次绑定时冻结；未识别不使创建失败。</li>
 */
@Component
@AllArgsConstructor
public class CreateCardInstrumentProcessor
    extends AbstractProcessor<CreateCardInstrumentRequest, CreatedCardInstrument> {
    private final CardCallerService cardCallerService;
    private final CardService cardService;
    private final CardStorageService cardStorageService;
    private final PrepareCardInstrumentProcessor prepareCardInstrumentProcessor;
    private final CheckCardInstrumentProcessor checkCardInstrumentProcessor;
    private final CardBinRangeRepository cardBinRangeRepository;
    private final PaymentCardInstrumentRepository paymentCardInstrumentRepository;

    /** 只接受 Payment；卡来源必须恰好一种，Client、来源提交与期限上限必填。 */
    @Override
    protected void validate(CreateCardInstrumentRequest request) {
        cardCallerService.require(request.getCaller(), CardApiConstants.PAYMENT_SERVICE);
        CardInstrumentContext context = request.getContext();
        if (context.getClientId() == null || context.getSourceSubmissionId() == null || context.getCard() == null
            || context.getExpiresAt() == null) {
            throw new InvalidParameterException("卡工具创建请求无效");
        }
    }

    @Override
    protected CreatedCardInstrument processRequest(CreateCardInstrumentRequest request) {
        CardInstrumentContext context = request.getContext();
        PaymentCardInstrument original =
            paymentCardInstrumentRepository.findBySubmission(context.getClientId(), context.getSourceSubmissionId());
        if (original != null) {
            return replay(context, original);
        }
        prepareCardInstrumentProcessor.process(context);
        try {
            cardService.applyInstrument(context);
        } catch (DuplicateKeyException concurrent) {
            PaymentCardInstrument saved = paymentCardInstrumentRepository.findBySubmission(context.getClientId(),
                context.getSourceSubmissionId());
            if (saved == null) {
                throw new ErrorException("卡交易工具唯一冲突后未找到原工具: " + context.getSourceSubmissionId(), concurrent);
            }
            return replay(context, saved);
        }
        return result(cardStorageService.findInstrument(context.getInstrumentRef()));
    }

    /** 原提交重放：核对卡身份后返回原工具，不访问 Vault、不触碰 CVV。 */
    CreatedCardInstrument replay(CardInstrumentContext context, PaymentCardInstrument original) {
        context.setPreparedInstrument(null);
        context.setInstrumentRef(String.valueOf(original.getPaymentCardInstrumentId()));
        checkCardInstrumentProcessor.process(context);
        return result(cardStorageService.findInstrument(context.getInstrumentRef()));
    }

    /** 工具引用、掩码卡号、期限、风控属性与当前生效 BIN 库的卡属性快照。 */
    CreatedCardInstrument result(CardInstrumentRecord record) {
        if (record == null) {
            throw new ErrorException("卡交易工具保存后不存在");
        }
        PaymentCard card = record.card();
        return new CreatedCardInstrument(String.valueOf(record.instrument().getPaymentCardInstrumentId()),
            maskedPan(card), record.instrument().getExpiresAt(),
            new CardRiskAttributes(card.getCardBin(), card.getCardFingerprint()),
            CardAttributes.of(cardBinRangeRepository.findMatch(card.getCardBin(), null)));
    }

    /** 前 6 位、6 个星号与后 4 位。 */
    public static String maskedPan(PaymentCard card) {
        return card.getCardBin().substring(0, 6) + "******" + card.getPanLast4();
    }
}
