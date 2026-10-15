package com.paytech.payment.card.integration.impl;

import java.util.Base64;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.payment.card.core.crypto.CardCipher;
import com.paytech.payment.card.integration.CardKeyClient;
import com.paytech.vault.client.ProtectCommand;
import com.paytech.vault.client.RevealQuery;
import com.paytech.vault.client.TokenMode;
import com.paytech.vault.client.VaultClient;

import lombok.AllArgsConstructor;

/**
 * 经 Vault 托管本卡 DEK。
 *
 * <p>
 * Vault 的拒绝与不可用对申请方都是服务端问题，统一转为服务不可用（503）并保留 Cause，不把 Vault 错误码透给调用方。
 */
@Component
@AllArgsConstructor
public class CardKeyClientImpl implements CardKeyClient {
    /** Vault 数据类型。 */
    public static final String DATA_TYPE = "PAYMENT_CARD_DEK";

    /** 保存本卡 DEK 的用途。 */
    public static final String PROTECT_PURPOSE = "CARD_STORAGE";

    /** 读卡与原提交核对取回 DEK 的用途。 */
    public static final String REVEAL_PURPOSE = "CARD_USE";

    private final VaultClient vaultClient;

    /** 以卡 ID 为幂等键保护 DEK。 */
    @Override
    public String protect(long paymentCardId, byte[] dataKey) {
        var command = new ProtectCommand(DATA_TYPE, TokenMode.REFERENCE, Base64.getEncoder().encodeToString(dataKey),
            PROTECT_PURPOSE, "payment-card-" + paymentCardId);
        try {
            return vaultClient.protect(command).token();
        } catch (FailException | ErrorException failed) {
            throw new ServiceUnavailableException(failed);
        }
    }

    /** 取回并解码 DEK；长度不符视为数据损坏。 */
    @Override
    public byte[] reveal(String dekVaultToken) {
        String value;
        try {
            value = vaultClient.reveal(new RevealQuery(dekVaultToken, REVEAL_PURPOSE)).value();
        } catch (FailException | ErrorException failed) {
            throw new ServiceUnavailableException(failed);
        }
        byte[] dataKey;
        try {
            dataKey = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException invalid) {
            throw new ErrorException("卡密钥内容无效");
        }
        if (dataKey.length != CardCipher.DATA_KEY_BYTES) {
            throw new ErrorException("卡密钥内容无效");
        }
        return dataKey;
    }
}
