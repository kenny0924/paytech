package com.paytech.platform.support.referencedata.domainservice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.FailException;
import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;
import com.paytech.platform.support.referencedata.domainservice.repository.ReferenceCurrencyRepository;
import com.paytech.platform.support.referencedata.domainservice.service.CurrencySpecQueryService;

import lombok.AllArgsConstructor;

/** 币种规则只读查询；单条 SELECT，不开事务。 */
@Service
@AllArgsConstructor
public class CurrencySpecQueryServiceImpl implements CurrencySpecQueryService {

    private final ReferenceCurrencyRepository repository;

    /** 全部币种，不限状态。 */
    @Override
    public List<ReferenceCurrency> listCurrencySpecs() {
        return repository.find(null, null);
    }

    /** 不存在时返回公共 NOT_FOUND。 */
    @Override
    public ReferenceCurrency getCurrencySpec(String currency) {
        ReferenceCurrency found = repository.findByCurrencyCode(currency);
        if (found == null) {
            throw new FailException(CommonErrorCode.NOT_FOUND);
        }
        return found;
    }
}
