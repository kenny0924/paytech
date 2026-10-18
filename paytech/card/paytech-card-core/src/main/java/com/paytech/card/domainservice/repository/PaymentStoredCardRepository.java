package com.paytech.card.domainservice.repository;

import java.time.Instant;
import java.util.List;

import com.paytech.card.core.dal.dataobject.PaymentStoredCard;

/** 已存卡数据访问 */
public interface PaymentStoredCardRepository {
    /**
     * 插入已存卡
     * <li>同一 Customer 同一张卡已有有效记录时抛出 {@link org.springframework.dao.DuplicateKeyException}。</li>
     */
    void insertStoredCard(PaymentStoredCard storedCard);

    /** 按对外 Token 查询；不存在时返回 null。 */
    PaymentStoredCard findByToken(String storedCardToken);

    /** 按 Customer 与卡查询有效已存卡；不存在时返回 null。 */
    PaymentStoredCard findActive(long clientId, long customerId, long paymentCardId);

    /** 按 Customer 与卡锁定读取有效已存卡（须在事务内）；用于唯一冲突后读取并发事务已提交的记录。 */
    PaymentStoredCard lockActive(long clientId, long customerId, long paymentCardId);

    /** 某 Customer 的全部有效已存卡。 */
    List<PaymentStoredCard> listActive(long clientId, long customerId);

    /**
     * 有效时更新当前凭证与同意时间，成功后同步对象
     * <li>在支付成功事务内调用，不比较版本、不抛并发异常，避免存卡冲突回滚支付成功；已被删除时不更新、不恢复。</li>
     */
    void updateCredential(PaymentStoredCard storedCard, long paymentCardCredentialId, Instant consentTime);

    /** 版本条件下删除，成功后同步对象；版本冲突抛出并发异常。 */
    void markDeleted(PaymentStoredCard storedCard, Instant deletedTime);

    /** 删除某 Customer 的全部有效已存卡，返回删除条数。 */
    int invalidateCustomer(long clientId, long customerId, Instant deletedTime);
}
