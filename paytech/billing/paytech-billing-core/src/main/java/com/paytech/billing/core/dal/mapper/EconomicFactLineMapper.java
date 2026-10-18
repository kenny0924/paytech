package com.paytech.billing.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;

/** t_economic_fact_line：经济事实明细行的登记与读取。 */
@Mapper
public interface EconomicFactLineMapper {

    /** 按经济事实读取全部明细行，按行号升序。 */
    List<EconomicFactLine> selectByEconomics(@Param("transactionEconomicsId") long transactionEconomicsId);

    /** 按一组经济事实读取全部明细行，按事实与行号升序。 */
    List<EconomicFactLine> selectByEconomicsIds(
        @Param("transactionEconomicsIds") Collection<Long> transactionEconomicsIds);

    /** 一次插入人工 / 成本调整的全部明细行；返回影响行数。 */
    int insertAll(@Param("lines") List<EconomicFactLine> lines);
}
