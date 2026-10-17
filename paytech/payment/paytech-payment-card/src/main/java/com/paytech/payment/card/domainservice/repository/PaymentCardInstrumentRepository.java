package com.paytech.payment.card.domainservice.repository;

import com.paytech.payment.card.core.dal.dataobject.PaymentCardInstrument;

/** 卡交易工具数据访问 */
public interface PaymentCardInstrumentRepository {
    /** 插入卡交易工具；交易工具 ID 由调用方分配（作为 CVV 的 AAD 与 Redis Key）。 */
    void insertInstrument(PaymentCardInstrument instrument);

    /** 按工具引用查询；引用不是有效的交易工具 ID 或记录不存在时返回 null。 */
    PaymentCardInstrument findInstrument(String instrumentRef);
}
