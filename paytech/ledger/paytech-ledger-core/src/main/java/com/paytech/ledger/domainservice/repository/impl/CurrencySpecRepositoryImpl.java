package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.ledger.core.dal.mapper.CurrencySpecMapper;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

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

    /** 读取币种规则；status 为空时不过滤，按币种代码升序。 */
    @Override
    public List<CurrencySpec> findAll(CurrencySpecStatus status) {
        return mapper.selectAll(status);
    }
}
