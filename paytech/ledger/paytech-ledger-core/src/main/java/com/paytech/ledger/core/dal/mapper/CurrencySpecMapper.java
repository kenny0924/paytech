package com.paytech.ledger.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.CurrencySpec;

/** t_currency_spec：币种规则只读访问。 */
@Mapper
public interface CurrencySpecMapper {

    /** 按 ISO 4217 代码读取币种规则。 */
    CurrencySpec selectByCurrency(@Param("currency") String currency);
}
