package com.paytech.acquiring.domainservice.repository.customer;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContract;

/** ConsumerContract 的本库持久化边界。 */
public interface ConsumerContractRepository {
    /** 保存不可变准备事实。 */
    void insert(FrozenCheckoutRecord row);
    /** 查找本订单当前（最新呈现）且法律关系完整的合同；报价变化后每个税务计算一份，取最新一份。 */
    ConsumerContract findByOrderId(long orderId);
}

