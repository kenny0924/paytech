package com.paytech.payment.card.service.processor;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.payment.card.core.crypto.CardCipher;
import com.paytech.payment.card.domain.CardInstrumentContext.CardInput;
import com.paytech.payment.card.domain.CardInstrumentRecord;
import com.paytech.payment.card.domain.ChannelCardMaterial;
import com.paytech.payment.card.integration.CardKeyClient;
import com.paytech.payment.card.integration.CardSecurityCodeClient;
import com.paytech.payment.card.service.CardStorageService;

import lombok.AllArgsConstructor;

/**
 * 为渠道资金动作领取卡材料（卡数据经 Platform 渠道执行决定，2026-10-14）；请求为 Card 工具引用。
 *
 * <p>
 * 调用方已核对工具与支付尝试的绑定及当前允许用卡。读取交易工具及其凭证与卡 → 从 Vault 取回本卡 DEK → 解密卡号与凭证 → Redis GETDEL 领取本交易工具的 CVV → 解密 CVV。先解密再领取，
 * 避免解密失败白白消耗一次性 CVV；CVV 已领取或已过期时拒绝，不返回部分材料。DEK 明文用完清零。
 */
@Component
@AllArgsConstructor
public class ClaimChannelCardMaterialProcessor extends AbstractProcessor<String, ChannelCardMaterial> {
    private final CardCipher cardCipher;
    private final CardKeyClient cardKeyClient;
    private final CardSecurityCodeClient cardSecurityCodeClient;
    private final CardStorageService cardStorageService;

    @Override
    protected ChannelCardMaterial processRequest(String instrumentRef) {
        CardInstrumentRecord record = cardStorageService.findInstrument(instrumentRef);
        if (record == null) {
            throw new BizCheckFailException("卡材料不存在或已过期");
        }
        long paymentCardInstrumentId = record.instrument().getPaymentCardInstrumentId();
        byte[] dataKey = cardKeyClient.reveal(record.card().getDekVaultToken());
        try {
            String pan = cardCipher.decryptPan(dataKey, record.card().getPaymentCardId(), record.card().getEncryptedPan());
            CardInput details = cardCipher.decryptDetails(dataKey, record.credential().getPaymentCardCredentialId(),
                record.credential().getEncryptedDetails());
            String encryptedSecurityCode = cardSecurityCodeClient.claim(paymentCardInstrumentId);
            if (encryptedSecurityCode == null) {
                throw new BizCheckFailException("卡安全码已领取或已过期，不能再次用于渠道执行");
            }
            return new ChannelCardMaterial(pan, details.getExpiryMonth(), details.getExpiryYear(),
                details.getCardholderName(),
                cardCipher.decryptSecurityCode(dataKey, paymentCardInstrumentId, encryptedSecurityCode));
        } finally {
            Arrays.fill(dataKey, (byte)0);
        }
    }
}
