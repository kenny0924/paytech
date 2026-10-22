package com.paytech.acquiring.integration.payment;

import java.time.Instant;
import java.util.List;

/**
 * Payment 已存卡列表端口：只在数据库事务外调用；结账页展示已存卡。
 * <li>acquiring 已经 consumer 核对存卡资格；Payment 按 Client 与 Customer 再次限定。</li>
 * <li>依赖不可用时抛出 {@code ServiceUnavailableException}，协议不符时抛出上游协议异常。</li>
 */
public interface PaymentStoredCardClient {
    /**
     * 列出 Customer 有效且未过期的已存卡
     *
     * @param clientId Client ID
     * @param customerId consumer Customer 主键
     * @return 已存卡
     */
    List<StoredCard> list(long clientId, long customerId);

    /**
     * 对外可展示的已存卡
     *
     * @param storedCardToken 已存卡 Token
     * @param maskedPan 前 6 后 4 掩码卡号
     * @param last4 卡号后 4 位
     * @param cardBrand 卡组织 Code（card 按当前生效 BIN 库实时查询，只用于展示）；未识别为 null
     * @param expiryMonth 有效期月份
     * @param expiryYear 有效期年份
     * @param createdAt 保存时间
     */
    record StoredCard(String storedCardToken, String maskedPan, String last4, String cardBrand, String expiryMonth,
        String expiryYear, Instant createdAt) {}
}
