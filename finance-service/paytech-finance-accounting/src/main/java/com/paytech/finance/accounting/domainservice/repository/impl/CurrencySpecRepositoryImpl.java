package com.paytech.finance.accounting.domainservice.repository.impl;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.CurrencySpec;
import com.paytech.finance.accounting.core.dal.mapper.CurrencySpecMapper;
import com.paytech.finance.accounting.domainservice.repository.CurrencySpecRepository;

import lombok.AllArgsConstructor;

/** CurrencySpecRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class CurrencySpecRepositoryImpl implements CurrencySpecRepository {
    private final CurrencySpecMapper mapper;

    /** 按 ISO 4217 代码读取币种规则，不存在返回空。 */
    @Override
    public CurrencySpec findByCurrency(String currency) {
        return mapper.selectByCurrency(currency);
    }
}
