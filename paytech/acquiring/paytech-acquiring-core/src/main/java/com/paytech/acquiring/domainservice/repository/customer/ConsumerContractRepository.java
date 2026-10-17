package com.paytech.acquiring.domainservice.repository.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContract;

/** ConsumerContract 的本库持久化边界。 */
public interface ConsumerContractRepository {
    /** 保存不可变准备事实。 */
    void insert(FrozenCheckoutRecord row);
    /** 查找本订单已冻结且法律关系完整的合同。 */
    ConsumerContract findByOrderId(long orderId);
}

