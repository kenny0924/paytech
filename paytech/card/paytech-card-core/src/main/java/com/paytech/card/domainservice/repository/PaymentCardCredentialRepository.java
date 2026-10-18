package com.paytech.card.domainservice.repository;

import java.time.Instant;

import com.paytech.card.core.dal.dataobject.PaymentCardCredential;

/** 卡凭证数据访问 */
public interface PaymentCardCredentialRepository {
    /**
     * 插入卡凭证
     * <li>凭证 ID 由调用方在加密前分配（作为 AAD），本方法不重新分配。</li>
     * <li>同一张卡下摘要重复时抛出 {@link org.springframework.dao.DuplicateKeyException}。</li>
     */
    void insertCredential(PaymentCardCredential credential);

    /** 首次支付成功时写入验证时间；已验证时不改并同步对象。 */
    void markVerified(PaymentCardCredential credential, Instant verifiedTime);

    /** 按凭证 ID 查询；不存在时返回 null。 */
    PaymentCardCredential findCredential(long paymentCardCredentialId);

    /** 按卡与凭证摘要查询；不存在时返回 null。 */
    PaymentCardCredential findCredentialByDigest(long paymentCardId, String credentialDigest);
}
