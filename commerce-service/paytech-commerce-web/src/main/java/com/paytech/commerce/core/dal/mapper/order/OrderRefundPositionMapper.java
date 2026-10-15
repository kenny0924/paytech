package com.paytech.commerce.core.dal.mapper.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;

/** 退款位置表的查询、预占和终态记账 SQL。 */
public interface OrderRefundPositionMapper {
    /** 按订单普通读取额度。 */
    OrderRefundPosition findByOrderId(@Param("orderId") long orderId);

    /** 提交任务在本地事务中锁定额度。 */
    OrderRefundPosition lockByOrderId(@Param("orderId") long orderId);

    /** 插入已构造的本表记录。 */
    int insert(OrderRefundPosition value);

    /** 只更新金额位置，关联订单和支付投影仅用于本库资格条件。 */
    int reserve(@Param("position") OrderRefundPosition position, @Param("order") CommerceOrder order,
        @Param("payment") OrderPaymentProjection payment, @Param("amount") BigDecimal amount,
        @Param("now") Instant now);

    /** 支付成功时以版本和未使用条件设置可退款基数。 */
    int establishBase(@Param("position") OrderRefundPosition position, @Param("amount") BigDecimal amount,
        @Param("now") Instant now);

    /** 条件消费或释放已预占的金额。 */
    int settle(@Param("position") OrderRefundPosition position, @Param("amount") BigDecimal amount,
        @Param("success") boolean success, @Param("now") Instant now);
}
