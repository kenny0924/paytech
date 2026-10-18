package com.paytech.payment.execution.domain;

import java.time.Instant;

/**
 * card-service 返回的卡交易工具
 *
 * @param instrumentRef Card 工具引用
 * @param expiresAt 工具实际期限
 * @param cardBin 卡 BIN（风控输入）
 * @param cardFingerprint 卡指纹（受限值，风控输入）
 */
public record CardInstrumentResult(String instrumentRef, Instant expiresAt, String cardBin, String cardFingerprint) {
    @Override
    public String toString() {
        return "CardInstrumentResult[" + instrumentRef + "]";
    }
}
