package com.paytech.card.domain;

import java.time.Instant;

import com.paytech.card.domain.binlibrary.CardAttributes;

/**
 * 卡交易工具创建结果：不含卡号、CVV、DEK 或 Vault Token
 *
 * @param instrumentRef Card 工具引用
 * @param maskedPan 前 6 位、6 个星号与后 4 位
 * @param expiresAt 工具实际期限
 * @param riskAttributes 风控卡属性（BIN 与指纹）
 * @param cardAttributes 本次按当前生效 BIN 库查询的卡属性快照
 */
public record CreatedCardInstrument(String instrumentRef, String maskedPan, Instant expiresAt,
    CardRiskAttributes riskAttributes, CardAttributes cardAttributes) {
    @Override
    public String toString() {
        return "CreatedCardInstrument[" + instrumentRef + "]";
    }
}
