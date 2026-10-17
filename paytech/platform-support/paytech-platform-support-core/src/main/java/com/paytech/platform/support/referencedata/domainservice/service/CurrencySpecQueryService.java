package com.paytech.platform.support.referencedata.domainservice.service;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** 全平台币种规则查询能力。 */
public interface CurrencySpecQueryService {

    /** 返回全部币种规则，按币种代码升序。 */
    List<CurrencySpec> listCurrencySpecs();

    /**
     * 返回指定币种的规则。
     *
     * @param currency ISO 4217 代码
     * @return 规则
     * @throws com.paytech.foundation.common.error.FailException 该币种没有规则（404 NOT_FOUND）
     */
    CurrencySpec getCurrencySpec(String currency);
}
