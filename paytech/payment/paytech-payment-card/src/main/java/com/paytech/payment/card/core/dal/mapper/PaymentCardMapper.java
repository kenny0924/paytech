package com.paytech.payment.card.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.card.core.dal.dataobject.PaymentCard;

/** 卡 SQL 映射 */
@Mapper
public interface PaymentCardMapper {
    /** 插入新卡；卡指纹重复时由唯一索引拒绝。 */
    int insertCard(PaymentCard card);

    /** 按卡 ID 查询。 */
    PaymentCard findCard(@Param("paymentCardId") long paymentCardId);

    /** 按卡指纹查询。 */
    PaymentCard findCardByFingerprint(@Param("cardFingerprint") String cardFingerprint);
}
