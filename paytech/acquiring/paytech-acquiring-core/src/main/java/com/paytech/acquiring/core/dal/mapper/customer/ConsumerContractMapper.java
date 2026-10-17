package com.paytech.commerce.core.dal.mapper.customer;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerContract;

import org.apache.ibatis.annotations.Param;

/** ConsumerContract 的 XML SQL 入口。 */
public interface ConsumerContractMapper {
    /** 查找本订单已冻结且法律关系完整的合同。 */
    ConsumerContract findByOrderId(@Param("orderId") long orderId);
}

