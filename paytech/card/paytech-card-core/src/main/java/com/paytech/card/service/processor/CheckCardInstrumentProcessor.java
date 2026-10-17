package com.paytech.card.service.processor;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.card.core.crypto.CardCipher;
import com.paytech.card.domain.CardInstrumentContext;
import com.paytech.card.domain.CardInstrumentContext.CardInput;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.core.dal.dataobject.PaymentStoredCard;
import com.paytech.card.domain.CardInstrumentRecord;
import com.paytech.card.domainservice.repository.PaymentStoredCardRepository;
import com.paytech.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/**
 * 原提交重放时，在事务外核对本次卡输入与原交易工具是同一张卡、同一组凭证。
 *
 * <p>
 * 读取原交易工具及其凭证与卡 → 用本次输入计算卡指纹与凭证摘要 → 与记录比较（不比较 CVV）。不同则拒绝更换；不访问 Vault、不重新准备材料、不续期、不触碰 Redis 中的 CVV。
 * 原交易工具缺失属于数据问题，不当作新申请。Customer 与是否保存也必须与原提交一致；用已存卡时比较已存卡，手输卡时比较指纹与凭证摘要。
 */
@Component
@AllArgsConstructor
public class CheckCardInstrumentProcessor extends AbstractProcessor<CardInstrumentContext, CardInstrumentContext> {
    private final CardCipher cardCipher;
    private final CardStorageService cardStorageService;
    private final PaymentStoredCardRepository paymentStoredCardRepository;

    /** 重放必须带原工具引用与本次卡输入。 */
    @Override
    protected void validate(CardInstrumentContext context) {
        if (context.getCard() == null || context.getInstrumentRef() == null) {
            throw new ErrorException("原卡工具核对的上下文无效");
        }
    }

    @Override
    protected CardInstrumentContext processRequest(CardInstrumentContext context) {
        CardInstrumentRecord record = cardStorageService.findInstrument(context.getInstrumentRef());
        if (record == null) {
            throw new ErrorException("原卡交易工具不存在");
        }
        if (!sameIntent(record.instrument(), context) || !sameCard(record, context)) {
            throw new ConcurrentRequestException("原提交不可更换卡工具");
        }
        return context;
    }

    /** Customer 与是否保存与原提交一致。 */
    static boolean sameIntent(PaymentCardInstrument instrument, CardInstrumentContext context) {
        return Objects.equals(instrument.getCustomerId(), context.getCustomerId())
            && instrument.isSaveRequested() == context.isSaveCard();
    }

    /** 已存卡比较已存卡身份；手输卡比较卡指纹与凭证摘要。 */
    boolean sameCard(CardInstrumentRecord record, CardInstrumentContext context) {
        Long originalStoredCardId = record.instrument().getPaymentStoredCardId();
        if (context.getStoredCardToken() != null) {
            PaymentStoredCard storedCard = paymentStoredCardRepository.findByToken(context.getStoredCardToken());
            return storedCard != null && storedCard.getPaymentStoredCardId().equals(originalStoredCardId);
        }
        CardInput card = context.getCard();
        return originalStoredCardId == null
            && cardCipher.fingerprint(card.getPan()).equals(record.card().getCardFingerprint())
            && cardCipher.credentialDigest(card).equals(record.credential().getCredentialDigest());
    }
}
