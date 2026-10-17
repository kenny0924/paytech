package com.paytech.commerce.core.dal.dataobject.order;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;

import com.paytech.commerce.domain.enums.CheckoutSessionStatus;
import com.paytech.commerce.client.api.v1.enums.OrderCreationMode;
import com.paytech.commerce.domain.enums.OrderStatus;

/** Orders 用例读取的本库事实，不跨 HTTP 暴露。 */
@Builder(toBuilder = true)
public record OrderRecord(
        /** 本库订单主键。 */
        long orderId,
        /** 对外大小写敏感订单号。 */
        String orderNo,
        /** 订单归属 Client。 */
        long clientId,
        /** Client 命名空间内的业务订单号。 */
        String clientOrderId,
        /** 创建时固定的接入方式，不从当前 Session 是否存在反推。 */
        OrderCreationMode creationMode,
        /** 准备选择的 OE；未选择为空。 */
        String entityNo,
        /** 支付准备命令；未开始为空。 */
        Long preparationCommandId,
        /** 持久化终止意图；未请求为空。 */
        Instant terminationRequestedAt,
        /** 当前订单状态。 */
        OrderStatus orderStatus,
        /** 订单 CAS 版本。 */
        long version,
        /** 优惠前商品金额。 */
        BigDecimal merchandiseAmount,
        /** 商品优惠金额。 */
        BigDecimal discountAmount,
        /** 商品净额。 */
        BigDecimal netAmount,
        /** 税额；未准备为空。 */
        BigDecimal taxAmount,
        /** 舍入金额；未准备为空。 */
        BigDecimal roundingAmount,
        /** 最终总额；未准备为空。 */
        BigDecimal totalAmount,
        /** ISO 币种。 */
        String currency,
        /** 原始到期时刻。 */
        Instant expiresAt,
        /** 支付成功时刻；未成功为空。 */
        Instant paidAt,
        /** 订单创建 UTC 时刻。 */
        Instant createTime,
        /** 订单最近更新时刻。 */
        Instant updateTime,
        /** Session 状态；Hosted Checkout 订单必须存在，其他模式可无 Session。 */
        CheckoutSessionStatus sessionStatus,
        /** Session 创建时固定的公开 Token，仅在服务内用于拼接支付链接。 */
        String publicToken,
        /** Session 创建时固定的公开 HTTPS Origin。 */
        String publicOrigin,
        /** Session CAS 版本；缺失时为空。 */
        Long sessionVersion,
        /** Session 最新持久化时刻。 */
        Instant sessionUpdateTime,
        /** 已确认取消的 UTC 时刻。 */
        Instant cancelledAt,
        /** 是否存在最终 Order Context。 */
        boolean contextPresent,
        /** 订单或最终 Context 中固定的消费者国家。 */
        String consumerCountryCode) {
}
