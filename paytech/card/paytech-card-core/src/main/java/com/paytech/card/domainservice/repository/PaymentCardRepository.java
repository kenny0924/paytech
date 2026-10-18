package com.paytech.card.domainservice.repository;

import com.paytech.card.core.dal.dataobject.PaymentCard;

/** 卡数据访问 */
public interface PaymentCardRepository {
    /**
     * 插入卡
     * <li>卡 ID 由调用方在加密前分配（作为 AAD 与 Vault 幂等键），本方法不重新分配。</li>
     * <li>卡指纹重复时抛出 {@link org.springframework.dao.DuplicateKeyException}，由调用方决定是否改读已有卡。</li>
     */
    void insertCard(PaymentCard card);

    /** 按卡 ID 查询；不存在时返回 null。 */
    PaymentCard findCard(long paymentCardId);

    /** 按卡指纹查询；不存在时返回 null。 */
    PaymentCard findCardByFingerprint(String cardFingerprint);
}
