package com.paytech.payment.card.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.card.core.dal.dataobject.PaymentStoredCard;

/** 已存卡 SQL 映射 */
@Mapper
public interface PaymentStoredCardMapper {
    /** 插入新已存卡；同一 Customer 同一张卡已有有效记录时由唯一索引拒绝。 */
    int insertStoredCard(PaymentStoredCard storedCard);

    /** 按对外 Token 查询。 */
    PaymentStoredCard findByToken(@Param("storedCardToken") String storedCardToken);

    /** 按 Customer 与卡查询有效已存卡。 */
    PaymentStoredCard findActive(@Param("clientId") long clientId, @Param("customerId") long customerId,
        @Param("paymentCardId") long paymentCardId);

    /** 按 Customer 与卡锁定读取有效已存卡，读到其他事务已提交的最新记录。 */
    PaymentStoredCard lockActive(@Param("clientId") long clientId, @Param("customerId") long customerId,
        @Param("paymentCardId") long paymentCardId);

    /** 某 Customer 的全部有效已存卡，按创建先后排列。 */
    List<PaymentStoredCard> listActive(@Param("clientId") long clientId, @Param("customerId") long customerId);

    /** 有效时更新当前凭证与同意时间（后到者覆盖，不比较版本）。 */
    int updateCredential(@Param("paymentStoredCardId") long paymentStoredCardId,
        @Param("paymentCardCredentialId") long paymentCardCredentialId, @Param("consentTime") Instant consentTime);

    /** 版本条件下删除一条有效已存卡。 */
    int markDeleted(@Param("paymentStoredCardId") long paymentStoredCardId, @Param("deletedTime") Instant deletedTime,
        @Param("version") long version);

    /** 删除某 Customer 的全部有效已存卡，返回影响行数。 */
    int invalidateCustomer(@Param("clientId") long clientId, @Param("customerId") long customerId,
        @Param("deletedTime") Instant deletedTime);
}
