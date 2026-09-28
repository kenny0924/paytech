package com.paytech.commerce.core.dal.mapper.order;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;

/** 订单支付投影的单表查询。 */
public interface OrderPaymentProjectionMapper {
    /** 只读取当前订单支付投影版本。 */
    Long findVersionByOrderId(@Param("orderId") long orderId);

    /** 读取本订单绑定的 Intent、成功事实和投影版本。 */
    OrderPaymentProjection findByOrderId(@Param("orderId") long orderId);
    /** 插入唯一订单与 Intent 关联。 */
    int insert(OrderPaymentProjection payment);
}
