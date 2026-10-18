package com.paytech.platform.support.referencedata.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.core.dal.mapper.CurrencySpecMapper;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;

import lombok.AllArgsConstructor;

/** 委托 Mapper 的币种规则仓储。 */
@Repository
@AllArgsConstructor
public class CurrencySpecRepositoryImpl implements CurrencySpecRepository {

    private final CurrencySpecMapper mapper;

    /** 全部币种规则。 */
    @Override
    public List<CurrencySpec> findAll() {
        return mapper.selectAll();
    }

    /** 按币种代码查询。 */
    @Override
    public CurrencySpec findByCurrency(String currency) {
        return mapper.selectByCurrency(currency);
    }
}
