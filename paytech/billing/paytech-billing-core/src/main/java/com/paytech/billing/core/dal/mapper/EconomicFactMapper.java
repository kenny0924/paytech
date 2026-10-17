package com.paytech.billing.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.EconomicFact;

/** t_economic_fact：经济事实只读访问。 */
@Mapper
public interface EconomicFactMapper {

    /** 按主键读取。 */
    EconomicFact selectById(@Param("id") long id);
}
