package com.paytech.platform.support.referencedata.domainservice.service;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.ReferenceCurrency;

/** 全平台币种规则查询能力（旧接口 /platform/v1/currency-specs）。 */
public interface CurrencySpecQueryService {

    /** 返回全部币种，按币种代码升序。 */
    List<ReferenceCurrency> listCurrencySpecs();

    /**
     * 返回指定币种。
     *
     * @param currency ISO 4217 代码
     * @return 币种
     * @throws com.paytech.foundation.common.error.FailException 该币种不存在（404 NOT_FOUND）
     */
    ReferenceCurrency getCurrencySpec(String currency);
}
