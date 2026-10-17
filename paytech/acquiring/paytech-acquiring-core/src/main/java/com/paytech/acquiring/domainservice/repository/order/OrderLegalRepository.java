package com.paytech.acquiring.domainservice.repository.order;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;

/** 订单法律事实的本库写入边界。 */
public interface OrderLegalRepository {
    /** 插入原卖方、合同及文档版本。 */
    void insert(FrozenCheckoutRecord row);
}
