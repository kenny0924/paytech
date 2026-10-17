package com.paytech.payment.card.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.payment.card.core.dal.dataobject.PaymentCardInstrument;

/** 卡交易工具 SQL 映射 */
@Mapper
public interface PaymentCardInstrumentMapper {
    /** 插入新交易工具。 */
    int insertInstrument(PaymentCardInstrument instrument);

    /** 按交易工具 ID 查询。 */
    PaymentCardInstrument findInstrument(@Param("paymentCardInstrumentId") long paymentCardInstrumentId);
}
