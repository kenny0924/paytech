package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomicComponent;

/** t_transaction_economic_component：交易经济组件只读访问。 */
@Mapper
public interface TransactionEconomicComponentMapper {

    /** 按交易经济结果读取全部组件，按组件序号升序。 */
    List<TransactionEconomicComponent> selectByEconomics(@Param("transactionEconomicsId") long transactionEconomicsId);
}
