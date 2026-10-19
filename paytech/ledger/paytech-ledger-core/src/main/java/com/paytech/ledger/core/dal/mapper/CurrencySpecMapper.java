package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.CurrencySpec;
import com.paytech.platform.support.api.enums.CurrencySpecStatus;

/** t_currency_spec：币种规则只读访问。 */
@Mapper
public interface CurrencySpecMapper {

    /** 按 ISO 4217 代码读取币种规则。 */
    CurrencySpec selectByCurrency(@Param("currency") String currency);

    /** 读取币种规则，可按状态过滤，按币种代码升序。 */
    List<CurrencySpec> selectAll(@Param("status") CurrencySpecStatus status);
}
