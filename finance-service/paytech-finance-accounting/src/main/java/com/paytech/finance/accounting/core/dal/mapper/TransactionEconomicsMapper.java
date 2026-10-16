package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomics;

/** t_transaction_economics：交易经济结果只读访问。 */
@Mapper
public interface TransactionEconomicsMapper {

    /** 按主键读取。 */
    TransactionEconomics selectById(@Param("id") long id);
}
