package com.paytech.commerce.domainservice.repository.order;

import java.util.List;

import com.paytech.commerce.core.dal.dataobject.order.*;

/** 本库订单查询边界；取消写入由各业务表 Repository 承担。 */
public interface OrderRepository {
    /** 按 Client 隔离读取订单；仅取消等写操作在事务内使用锁定读取。 */
    OrderRecord find(long clientId, String orderNo, String clientOrderId, boolean lock);

    /** 返回订单创建时保存的不可变商品列表。 */
    List<OrderItem> items(long clientId, long orderId);

    /** 读取可为空的支付投影，不调用 Payment。 */
    OrderPaymentRecord payment(long orderId);

    /** 读取必需的本地退款金额位置。 */
    OrderRefundPosition refund(long orderId);

    /** 读取必需的本地资金可用投影。 */
    OrderFundsRecord funds(long orderId);

}
