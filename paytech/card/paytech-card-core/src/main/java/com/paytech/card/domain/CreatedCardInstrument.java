package com.paytech.card.domain;

import java.time.Instant;

/**
 * 卡交易工具创建结果：不含卡号、CVV、DEK 或 Vault Token
 *
 * @param instrumentRef Card 工具引用
 * @param maskedPan 前 6 位、6 个星号与后 4 位
 * @param expiresAt 工具实际期限
 * @param riskAttributes 风控卡属性（BIN 与指纹）
 */
public record CreatedCardInstrument(String instrumentRef, String maskedPan, Instant expiresAt,
    CardRiskAttributes riskAttributes) {
    @Override
    public String toString() {
        return "CreatedCardInstrument[" + instrumentRef + "]";
    }
}
