package com.paytech.billing.domainservice.repository;

import com.paytech.billing.core.dal.dataobject.CurrencySpec;

/** t_CurrencySpec：币种规则读取。 */
public interface CurrencySpecRepository {

    /** 按 ISO 4217 代码读取币种规则，不存在返回空。 */
    CurrencySpec findByCurrency(String currency);
}
