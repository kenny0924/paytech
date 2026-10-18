package com.paytech.card.service;

import java.util.List;

import com.paytech.card.domain.StoredCardView;

/**
 * 已存卡管理：只访问 Card 自己的表，不访问 Vault、Redis 等外部依赖
 * <li>调用方（Commerce）已核对 Client 与 Customer；这里按二者再次限定，跨 Customer 视为不存在。</li>
 */
public interface StoredCardService {
    /**
     * 列出某 Customer 有效且未过期的已存卡
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer ID
     * @return 按创建先后排列的已存卡
     */
    List<StoredCardView> listStoredCards(long clientId, long customerId);

    /**
     * 删除一张已存卡；已删除的再次删除直接返回
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer ID
     * @param storedCardToken 对外已存卡 Token
     */
    void deleteStoredCard(long clientId, long customerId, String storedCardToken);

    /**
     * 让某 Customer 的全部有效已存卡失效；可重复调用
     *
     * @param clientId Client ID
     * @param customerId Commerce Customer ID
     * @return 本次失效的条数
     */
    int invalidateCustomer(long clientId, long customerId);
}
