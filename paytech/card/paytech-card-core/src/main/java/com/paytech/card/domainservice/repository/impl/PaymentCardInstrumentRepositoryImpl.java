package com.paytech.card.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.card.core.dal.dataobject.PaymentCardInstrument;
import com.paytech.card.core.dal.mapper.PaymentCardInstrumentMapper;
import com.paytech.card.domainservice.repository.PaymentCardInstrumentRepository;

import lombok.AllArgsConstructor;

/** 卡交易工具数据访问 */
@Repository
@AllArgsConstructor
public class PaymentCardInstrumentRepositoryImpl implements PaymentCardInstrumentRepository {
    private final PaymentCardInstrumentMapper paymentCardInstrumentMapper;

    /** 插入一行；影响行数不为 1 视为系统错误。 */
    @Override
    public void insertInstrument(PaymentCardInstrument instrument) {
        if (paymentCardInstrumentMapper.insertInstrument(instrument) != 1) {
            throw new ErrorException("卡交易工具保存失败: " + instrument.getPaymentCardInstrumentId());
        }
    }

    /** 工具引用为交易工具 ID 的十进制字符串。 */
    @Override
    public PaymentCardInstrument findInstrument(String instrumentRef) {
        if (instrumentRef == null || !instrumentRef.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        long paymentCardInstrumentId;
        try {
            paymentCardInstrumentId = Long.parseLong(instrumentRef);
        } catch (NumberFormatException outOfRange) {
            return null;
        }
        return paymentCardInstrumentMapper.findInstrument(paymentCardInstrumentId);
    }
}
