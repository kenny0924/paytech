package com.paytech.acquiring.domainservice.repository.order;

import com.paytech.acquiring.core.dal.dataobject.customer.FrozenCheckoutRecord;
import com.paytech.acquiring.domain.customer.CheckoutLegalView;
import com.paytech.acquiring.domain.order.OrderCommercialContextView;

/** 冻结订单上下文的查询边界。 */
public interface OrderContextRepository {
    /** 保存不可变准备事实。 */
    void insert(FrozenCheckoutRecord row);
    /** 读取本上下文关联的卖方与合同展示字段，保留关联缺失事实。 */
    CheckoutLegalView findCheckoutLegal(long orderId);
    /** 读取原订单的三表冻结事实。 */
    FrozenCheckoutRecord findFrozen(long orderId);
    /** 读取订单及其冻结上下文、法律事实的商业字段；订单不存在返回 null，上下文缺失时对应字段为空。 */
    OrderCommercialContextView findCommercialContext(long orderId);
}
