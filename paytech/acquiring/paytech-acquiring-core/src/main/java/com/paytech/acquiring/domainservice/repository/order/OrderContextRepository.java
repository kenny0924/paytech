package com.paytech.commerce.domainservice.repository.order;

import com.paytech.commerce.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.commerce.domain.customer.CheckoutLegalView;

/** 冻结订单上下文的查询边界。 */
public interface OrderContextRepository {
    /** 保存不可变准备事实。 */
    void insert(FrozenCheckoutRecord row);
    /** 读取本上下文关联的卖方与合同展示字段，保留关联缺失事实。 */
    CheckoutLegalView findCheckoutLegal(long orderId);
    /** 读取原订单的三表冻结事实。 */
    FrozenCheckoutRecord findFrozen(long orderId);
}
