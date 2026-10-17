package com.paytech.platform.support.referencedata.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.referencedata.domainservice.repository.CurrencySpecRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecQueryService;

import lombok.AllArgsConstructor;

/** 币种规则只读查询；单条 SELECT，不开事务。 */
@Service
@AllArgsConstructor
public class CurrencySpecQueryServiceImpl implements CurrencySpecQueryService {

    private final CurrencySpecRepository repository;

    /** 全部规则。 */
    @Override
    public List<CurrencySpec> listCurrencySpecs() {
        return repository.findAll();
    }

    /** 不存在时返回公共 NOT_FOUND。 */
    @Override
    public CurrencySpec getCurrencySpec(String currency) {
        CurrencySpec spec = repository.findByCurrency(currency);
        if (spec == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return spec;
    }
}
