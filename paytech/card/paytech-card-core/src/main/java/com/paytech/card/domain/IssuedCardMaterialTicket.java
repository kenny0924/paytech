package com.paytech.card.domain;

import java.time.Instant;

/**
 * 已签发的一次性读卡票据：票据原值只在本次响应内存中传递
 *
 * @param ticket 票据原值
 * @param expiresAt 截止时间
 */
public record IssuedCardMaterialTicket(String ticket, Instant expiresAt) {
    @Override
    public String toString() {
        return "IssuedCardMaterialTicket[REDACTED]";
    }
}
