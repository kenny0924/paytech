package com.paytech.tax.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.tax.core.dal.dataobject.TaxMarket;

/** t_tax_market：准入读取。 */
@Mapper
public interface TaxMarketMapper {
    /** 纳税主体的全部准入记录（任意地区、任意状态）。 */
    List<TaxMarket> selectBySubject(@Param("subjectPartyId") long subjectPartyId);

    /** 按 ID 批量读取。 */
    List<TaxMarket> selectByIds(@Param("ids") Collection<Long> ids);
}
