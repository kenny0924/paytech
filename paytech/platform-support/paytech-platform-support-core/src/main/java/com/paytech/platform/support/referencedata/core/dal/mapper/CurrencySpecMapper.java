package com.paytech.platform.support.referencedata.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.referencedata.core.dal.dataobject.CurrencySpec;

/** t_currency_spec 只读查询。 */
public interface CurrencySpecMapper {

    /**
     * 查询全部币种规则。
     *
     * @return 按币种代码升序
     */
    List<CurrencySpec> selectAll();

    /**
     * 按币种代码查询。
     *
     * @param currency ISO 4217 代码
     * @return 规则；不存在为 null
     */
    CurrencySpec selectByCurrency(@Param("currency") String currency);
}
