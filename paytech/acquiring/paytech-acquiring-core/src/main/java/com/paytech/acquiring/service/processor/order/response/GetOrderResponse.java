package com.paytech.commerce.service.processor.order.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.commerce.client.api.v1.enums.OrderCreationMode;
import com.paytech.commerce.domain.enums.*;

import lombok.Builder;

/** 订单查询的有限结果；不包含数据库身份、内部引用或完整持久化对象。 */
@Builder
public record GetOrderResponse(String orderNo, String clientOrderId, OrderCreationMode creationMode, OrderStatus orderStatus,
    Checkout checkout, boolean ready, boolean finalAmount,
    OrderPaymentStatus paymentStatus, String currency, String consumerCountryCode, List<Item> items, Amounts amounts,
    PaymentAttemptStatus lastAttemptStatus, RefundSummary refundSummary, FundsAvailability fundsAvailability,
    Instant createdAt, Instant expiresAt, Instant paidAt, Instant updatedAt) {

    /** 实际关联的收银台会话；当前会话与订单共用到期时刻。 */
    public record Checkout(CheckoutSessionStatus status, String url, Instant expiresAt) {
    }

    /** 商品展示事实；数量独立于币种精度，金额为币种主单位，优惠为整行金额。 */
    public record Item(String clientItemId, String name, String description, String productTypeCode,
        String taxCategoryCode, BigDecimal quantity, BigDecimal unitAmount, BigDecimal discountAmount) {
    }

    /** 订单币种主单位金额；准备前税额、舍入额与最终总额为空，未支付金额为零。 */
    public record Amounts(BigDecimal merchandiseAmount, BigDecimal discountAmount, BigDecimal netAmount,
        BigDecimal taxAmount, BigDecimal roundingAmount, BigDecimal totalAmount, BigDecimal paidAmount) {
    }

    /** 退款币种主单位金额；可退金额已扣除预占及成功退款。 */
    public record RefundSummary(BigDecimal processingAmount, BigDecimal succeededAmount, BigDecimal refundableAmount) {
    }

    /** 资金可用事实；未成熟时可用金额和时刻为空，不携带内部并发版本。 */
    public record FundsAvailability(FundsAvailabilityStatus status, BigDecimal availableAmount, Instant scheduledAt,
        Instant availableAt, Instant asOf, Instant updatedAt) {
    }
}
