package com.paytech.acquiring.core.dal.mapper.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContract;

import org.apache.ibatis.annotations.Param;

/** ConsumerContract 的 XML SQL 入口。 */
public interface ConsumerContractMapper {
    /** 查找本订单当前（最新呈现）且法律关系完整的合同；报价变化后每个税务计算一份，取最新一份。 */
    ConsumerContract findByOrderId(@Param("orderId") long orderId);
}

