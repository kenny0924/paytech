package com.paytech.platform.support.referencedata.domainservice.repository;

import java.util.List;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** 币种规则单表仓储。 */
public interface CurrencySpecRepository {

    /** 全部币种规则，按币种代码升序。 */
    List<CurrencySpec> findAll();

    /** 按币种代码查询；不存在返回 null。 */
    CurrencySpec findByCurrency(String currency);
}
