package com.paytech.commerce.core.dal.dataobject.checkout;
import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commerce.domain.enums.*;
/** 基础创建恢复时读取的 Order/Session 与后续准备边界。 */
public record CheckoutCreationView(
    /** 本库订单主键。 */
    long orderId,
    /** 不透明订单号。 */
    String orderNo,
    /** 可信 Client 主键。 */
    long clientId,
    /** Client 内订单号。 */
    String clientOrderId,

    /** 已选 OE，基础创建为空。 */
    String entityNo,
    /** 冻结商品金额。 */
    BigDecimal merchandiseAmount,
    /** 冻结优惠。 */
    BigDecimal discountAmount,
    /** 冻结净额。 */
    BigDecimal netAmount,
    /** 最终税额，准备前为空。 */
    BigDecimal taxAmount,
    /** 最终舍入，准备前为空。 */
    BigDecimal roundingAmount,
    /** 最终金额，准备前为空。 */
    BigDecimal totalAmount,
    /** 币种。 */
    String currency,
    /** 不可延长的到期时间。 */
    Instant expiresAt,
    /** 当前 Order 状态。 */
    OrderStatus orderStatus,
    /** Order CAS 版本。 */
    long orderVersion,
    /** 唯一 Session 主键。 */
    long checkoutSessionId,
    /** 原命令冻结的 Token。 */
    String publicToken,
    /** 原公开 Origin。 */
    String publicOrigin,
    /** 当前 Session 状态。 */
    CheckoutSessionStatus sessionStatus,
    /** Session CAS 版本。 */
    long sessionVersion,
    /** 准备命令存在后不能按基础创建关闭。 */
    Long preparationCommandId,
    /** 终止竞争意图。 */
    Instant terminationRequestedAt,
    /** 是否已有不可变上下文。 */
    boolean contextPresent,
    /** 是否已有 Payment 绑定。 */
    boolean paymentPresent) {
}
