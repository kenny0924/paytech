package com.paytech.card.domain;

import java.time.Instant;

/**
 * 对外可展示的已存卡：只有对外 Token、前 6 后 4 掩码卡号与有效期
 *
 * @param storedCardToken 对外已存卡 Token
 * @param maskedPan 前 6 位、6 个星号与后 4 位
 * @param last4 卡号后 4 位
 * @param expiryMonth 有效期月份，两位
 * @param expiryYear 有效期年份，四位
 * @param createdAt 已存卡创建时间
 */
public record StoredCardView(String storedCardToken, String maskedPan, String last4, String expiryMonth,
    String expiryYear, Instant createdAt) {}
