package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.FinanceEntity;

/** t_finance_entity：财务主体持久化。 */
@Mapper
public interface FinanceEntityMapper {

    /** 插入一行。 */
    int insert(FinanceEntity record);

    /** 按主键读取。 */
    FinanceEntity selectById(@Param("id") long id);
}
