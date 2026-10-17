package com.paytech.commerce.core.dal.mapper.checkout;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.checkout.*;
import com.paytech.commerce.core.dal.dataobject.order.OrderItem;
/** 新基础创建的准确持久化投影，不依赖旧 OE/Scope 列。 */
public interface BasicCheckoutMapper {
    /** 读取基础订单与会话视图；锁模式仅在本地事务中使用。 */
    CheckoutCreationView select(@Param("clientId") long clientId, @Param("orderId") long orderId,
        @Param("orderNo") String orderNo, @Param("lock") boolean lock);
    /** 按 Client 和 Order 返回原序商品。 */
    List<OrderItem> selectItems(@Param("clientId") long clientId, @Param("orderId") long orderId);
}
