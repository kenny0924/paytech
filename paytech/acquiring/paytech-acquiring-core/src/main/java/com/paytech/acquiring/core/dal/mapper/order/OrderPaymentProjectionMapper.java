package com.paytech.acquiring.core.dal.mapper.order;

import java.math.BigDecimal;
import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;

/** 订单支付投影的单表查询。 */
public interface OrderPaymentProjectionMapper {
    /** 只读取当前订单支付投影版本。 */
    Long findVersionByOrderId(@Param("orderId") long orderId);

    /** 读取本订单绑定的 Intent、成功事实和投影版本。 */
    OrderPaymentProjection findByOrderId(@Param("orderId") long orderId);
    /** 插入唯一订单与 Intent 关联。 */
    int insert(OrderPaymentProjection payment);

    /** 以投影版本和未成功条件记录支付成功事件。 */
    int recordSucceeded(@Param("payment") OrderPaymentProjection payment, @Param("attemptId") long attemptId,
        @Param("capturedAmount") BigDecimal capturedAmount, @Param("eventKey") String eventKey,
        @Param("payloadHash") String payloadHash, @Param("sourceVersion") long sourceVersion,
        @Param("eventTime") Instant eventTime, @Param("now") Instant now);

    /** 以投影版本和无成功、未捕获条件记录 Intent 已因订单过期终止。 */
    int recordTerminated(@Param("payment") OrderPaymentProjection payment, @Param("now") Instant now);
}
