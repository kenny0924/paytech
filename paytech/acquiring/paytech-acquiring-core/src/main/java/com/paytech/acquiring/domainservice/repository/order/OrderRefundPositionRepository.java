package com.paytech.acquiring.domainservice.repository.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;

/** OrderRefundPosition 的单表持久化边界。 */
public interface OrderRefundPositionRepository {
    /** 普通读取本地额度，不加锁或开启事务。 */
    OrderRefundPosition findByOrderId(long orderId);

    /** 提交任务按既有 Order、Position、Refund 顺序锁定额度。 */
    OrderRefundPosition lockByOrderId(long orderId);

    /** 插入一条记录，参与调用方本地事务。 */
    void insert(OrderRefundPosition value);

    /** 按预检版本和本库已支付条件预占金额，成功后同步原位置对象。 */
    void reserve(OrderRefundPosition position, CommerceOrder order, OrderPaymentProjection payment,
        BigDecimal amount, Instant now);

    /** 终态消费或释放预占，成功后同步原位置对象。 */
    void settle(OrderRefundPosition position, BigDecimal amount, boolean success, Instant now);

    /** 支付成功时设置可退款基数；要求原版本且尚无预占或退款，成功后同步原位置对象。 */
    void establishBase(OrderRefundPosition position, BigDecimal amount, Instant now);
}
