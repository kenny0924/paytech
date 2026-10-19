package com.paytech.ledger.domainservice.repository;

import java.util.List;

import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/** t_CurrencySpec：币种规则读取。 */
public interface CurrencySpecRepository {

    /** 按 ISO 4217 代码读取币种规则，不存在返回空。 */
    CurrencySpec findByCurrency(String currency);

    /** 读取币种规则；status 为空时不过滤，按币种代码升序。 */
    List<CurrencySpec> findAll(CurrencySpecStatus status);
}
