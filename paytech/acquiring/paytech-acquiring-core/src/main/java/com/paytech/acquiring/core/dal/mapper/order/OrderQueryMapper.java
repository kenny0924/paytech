package com.paytech.acquiring.core.dal.mapper.order;


import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRecord;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;

/** Orders 查询的本库 SQL 边界。 */
public interface OrderQueryMapper {
    /** 按可信 Client 和唯一业务标识读取订单；取消短事务可锁定订单及 Session。 */
    OrderRecord selectOrder(@Param("clientId") long clientId, @Param("orderNo") String orderNo,
            @Param("clientOrderId") String clientOrderId, @Param("lock") boolean lock);

    /** 读取 Payment 最新本地投影；尚未建立 Intent 时为空。 */
    OrderPaymentRecord selectPayment(long orderId);

    /** 读取本地可退款位置，调用方不能把缺失解释为零额。 */
    OrderRefundPosition selectRefund(long orderId);

    /** 读取 Finance 已应用事实形成的本地资金可用投影。 */
    OrderFundsRecord selectFunds(long orderId);

}
