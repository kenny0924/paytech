package com.paytech.billing.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;

/** t_economic_fact_line：经济事实明细行只读访问。 */
@Mapper
public interface EconomicFactLineMapper {

    /** 按交易经济结果读取全部组件，按组件序号升序。 */
    List<EconomicFactLine> selectByEconomics(@Param("transactionEconomicsId") long transactionEconomicsId);
}
