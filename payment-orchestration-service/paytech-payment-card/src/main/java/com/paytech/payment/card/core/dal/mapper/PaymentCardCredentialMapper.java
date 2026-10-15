package com.paytech.payment.card.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.card.core.dal.dataobject.PaymentCardCredential;

/** 卡凭证 SQL 映射 */
@Mapper
public interface PaymentCardCredentialMapper {
    /** 插入新凭证；同一张卡下摘要重复时由唯一索引拒绝。 */
    int insertCredential(PaymentCardCredential credential);

    /** 按凭证 ID 查询。 */
    PaymentCardCredential findCredential(@Param("paymentCardCredentialId") long paymentCardCredentialId);

    /** 按卡与凭证摘要查询。 */
    PaymentCardCredential findCredentialByDigest(@Param("paymentCardId") long paymentCardId,
        @Param("credentialDigest") String credentialDigest);
}
