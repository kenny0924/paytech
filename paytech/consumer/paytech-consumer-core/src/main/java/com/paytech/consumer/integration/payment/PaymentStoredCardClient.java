package com.paytech.consumer.integration.payment;

import java.time.Instant;
import java.util.List;

/**
 * Payment 已存卡端口：只在数据库事务外调用。
 * <li>Commerce 已核对 Client 与 Customer；Payment 按二者再次限定。</li>
 * <li>依赖不可用时抛出 {@code ServiceUnavailableException}，协议不符时抛出上游协议异常。</li>
 */
public interface PaymentStoredCardClient {
    /**
     * 列出 Customer 有效且未过期的已存卡
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer 主键
     * @return 已存卡
     */
    List<StoredCard> list(long clientId, long customerId);

    /**
     * 删除一张已存卡；已删除的再次删除同样成功
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer 主键
     * @param storedCardToken 已存卡 Token
     * @return 不存在或不属于该 Customer 时为 false
     */
    boolean delete(long clientId, long customerId, String storedCardToken);

    /**
     * 让 Customer 的全部有效已存卡失效；可重复调用
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer 主键
     * @return 本次失效条数
     */
    int invalidate(long clientId, long customerId);

    /**
     * 对外可展示的已存卡
     *
     * @param storedCardToken 已存卡 Token
     * @param maskedPan 前 6 后 4 掩码卡号
     * @param last4 卡号后 4 位
     * @param expiryMonth 有效期月份
     * @param expiryYear 有效期年份
     * @param createdAt 保存时间
     */
    record StoredCard(String storedCardToken, String maskedPan, String last4, String expiryMonth, String expiryYear,
        Instant createdAt) {}
}
