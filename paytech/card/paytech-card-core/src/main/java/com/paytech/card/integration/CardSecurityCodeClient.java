package com.paytech.card.integration;

import java.time.Duration;

/**
 * CVV 暂存端口：以本卡 DEK 加密后存 Redis，到期删除，只能领取一次。
 * <li>CVV 不进数据库、日志或 MQ；访问外部依赖，只能在数据库事务外由 Card Processor 调用。</li>
 */
public interface CardSecurityCodeClient {
    /**
     * 暂存加密后的 CVV
     *
     * @param paymentCardInstrumentId 卡交易工具 ID
     * @param encryptedSecurityCode 本卡 DEK 加密的 CVV
     * @param ttl 存活时间，等于工具剩余期限
     */
    void save(long paymentCardInstrumentId, String encryptedSecurityCode, Duration ttl);

    /**
     * 原子取出并删除 CVV
     *
     * @param paymentCardInstrumentId 卡交易工具 ID
     * @return 加密的 CVV；已领取或已过期时为 null
     */
    String claim(long paymentCardInstrumentId);

    /**
     * CVV 是否仍可领取（签发读卡票据前检查，不读取值）
     *
     * @param paymentCardInstrumentId 卡交易工具 ID
     * @return 未领取且未过期时为 true
     */
    boolean claimable(long paymentCardInstrumentId);
}
