package com.paytech.payment.card.service.processor;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.card.core.crypto.CardCipher;
import com.paytech.payment.card.domain.CardInstrumentContext;
import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;
import com.paytech.payment.card.domain.CardInstrumentRecord;
import com.paytech.payment.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/**
 * 原提交重放时，在事务外核对本次卡输入与原交易工具是同一张卡、同一组凭证。
 *
 * <p>
 * 读取原交易工具及其凭证与卡 → 用本次输入计算卡指纹与凭证摘要 → 与记录比较（不比较 CVV）。不同则拒绝更换；不访问 Vault、不重新准备材料、不续期、不触碰 Redis 中的 CVV。
 * 原交易工具缺失属于数据问题，不当作新申请。
 */
@Component
@AllArgsConstructor
public class CheckCardInstrumentProcessor extends AbstractProcessor<CardInstrumentContext, CardInstrumentContext> {
    private final CardCipher cardCipher;
    private final CardStorageService cardStorageService;

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
        CardInput card = context.getCard();
        if (!cardCipher.fingerprint(card.getPan()).equals(record.card().getCardFingerprint())
            || !cardCipher.credentialDigest(card).equals(record.credential().getCredentialDigest())) {
            throw new ConcurrentRequestException("原提交不可更换卡工具");
        }
        return context;
    }
}
