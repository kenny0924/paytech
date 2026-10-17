package com.paytech.commerce.core.dal.mapper.customer;

import java.util.List;

import com.paytech.commerce.core.dal.dataobject.customer.ConsumerAcceptance;

import org.apache.ibatis.annotations.Param;

/** ConsumerAcceptance 的 XML SQL 入口。 */
public interface ConsumerAcceptanceMapper {
    /** 读取准确合同的不可变接受记录，不拼接网络原值。 */
    List<ConsumerAcceptance> findByContractId(@Param("contractId") long contractId);

    /** 保存一条不可变接受记录，参与调用方事务。 */
    int insert(ConsumerAcceptance acceptance);
    /** 按订单检查已有法律接受。 */
    boolean existsByOrderId(long orderId);
}

