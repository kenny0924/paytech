package com.paytech.acquiring.domainservice.repository.customer;

import java.util.List;

import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerAcceptance;

/** ConsumerAcceptance 的本库持久化边界。 */
public interface ConsumerAcceptanceRepository {
    /** 读取准确合同的不可变接受记录，不拼接网络原值。 */
    List<ConsumerAcceptance> findByContractId(long contractId);

    /** 保存一条不可变接受记录，参与调用方事务。 */
    void insert(ConsumerAcceptance acceptance);
    /** 是否已受理过支付；用于编辑和取消门禁。 */
    boolean existsByOrderId(long orderId);
}

