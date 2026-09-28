package com.paytech.commerce.domainservice.repository.order;

import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;

/** 订单支付投影表的查询边界。 */
public interface OrderPaymentProjectionRepository {
    /** 返回前只核对本地支付版本；未绑定 Intent 时为空。 */
    Long findVersionByOrderId(long orderId);

    /** 读取取消判断所需的支付事实；尚无投影时返回空。 */
    OrderPaymentProjection findByOrderId(long orderId);
    /** 保存原订单的唯一 Intent 关联。 */
    void insert(OrderPaymentProjection payment);
}
