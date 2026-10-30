package com.paytech.acquiring.test.builder.order;

import com.paytech.acquiring.api.client.v1.enums.OrderAmountStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.paytech.acquiring.api.client.v1.enums.OrderCreationMode;
import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderFundsAvailabilityProjection;
import com.paytech.acquiring.core.dal.dataobject.order.OrderItem;
import com.paytech.acquiring.core.dal.dataobject.order.OrderRefundPosition;
import com.paytech.acquiring.domain.enums.*;
import com.paytech.foundation.token.PublicTokenGenerator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 查询前置事实 Builder；不调用创建/支付业务，允许用例显式制造不一致数据。 */
@Setter
@Accessors(fluent = true)
public final class GetOrderDataBuilder {
    public static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private long orderId;
    private long clientId;
    private String orderNo;
    private String clientOrderId = "query-order";
    private String currency = "USD";
    private boolean ready;
    private boolean paid;
    private boolean session = true;
    private OrderCreationMode creationMode = OrderCreationMode.HOSTED_CHECKOUT;

    /** 每次提供独立的最小合法待准备订单。 */
    public static GetOrderDataBuilder template() {
        return new GetOrderDataBuilder();
    }

    /** 具名构建已确定最终金额的订单。 */
    public GetOrderDataBuilder ready() {
        ready = true;
        return this;
    }

    /** 具名构建成功支付事实，退款基数与捕获金额均为 50。 */
    public GetOrderDataBuilder paid() {
        ready = true;
        paid = true;
        return this;
    }

    /** 显式构造 Direct Pay 无 Session 场景，不由生产创建接口生成前置数据。 */
    public GetOrderDataBuilder directPay() {
        creationMode = OrderCreationMode.DIRECT_PAY;
        session = false;
        return this;
    }

    /** ID 由 Mocker 预留；金额期望采用固定业务样例，不复用生产算法。 */
    public Fixture build() {
        var suffix = String.format(Locale.ROOT, "%030d", orderId);
        var order = CommerceOrder.builder().orderId(orderId).orderNo(orderNo == null ? "ord_" + suffix : orderNo)
            .clientId(clientId).clientOrderId(clientOrderId).creationMode(creationMode).consumerCountryCode("US")
            .entityNo(ready ? "AE001" : null)
            .merchandiseAmount(decimal("50")).discountAmount(decimal("2")).netAmount(decimal("48"))
            .taxAmount(ready ? decimal("2") : null).roundingAmount(ready ? decimal("0") : null)
            .totalAmount(ready ? decimal("50") : null)
            .amountStatus(ready ? OrderAmountStatus.FINAL : OrderAmountStatus.ESTIMATED).currency(currency)
            .orderStatus(paid ? OrderStatus.PAID : ready ? OrderStatus.OPEN : OrderStatus.CREATED)
            .expiresAt(NOW.plusSeconds(1800)).paidAt(paid ? NOW : null).version(7L)
            .createTime(NOW.minusSeconds(60)).updateTime(NOW).build();
        var checkout = !session ? null : CheckoutSession.builder().checkoutSessionId(orderId).orderId(orderId)
            .publicToken("chk_" + PublicTokenGenerator.generate(Long.toString(orderId)))
            .publicOrigin("https://checkout.local.test")
            .sessionStatus(paid ? CheckoutSessionStatus.COMPLETED : ready ? CheckoutSessionStatus.OPEN
                : CheckoutSessionStatus.PREPARING)
            .version(8L).createTime(NOW.minusSeconds(60)).updateTime(NOW).build();
        var refund = OrderRefundPosition.builder().orderRefundPositionId(orderId).orderId(orderId)
            .refundableBaseAmount(decimal(paid ? "50" : "0")).reservedAmount(decimal("0"))
            .refundedAmount(decimal("0")).currency(currency).version(9L)
            .createTime(NOW.minusSeconds(60)).updateTime(NOW).build();
        var funds = OrderFundsAvailabilityProjection.builder().orderFundsAvailabilityProjectionId(orderId)
            .orderId(orderId).fundsAvailabilityStatus(paid ? FundsAvailabilityStatus.PENDING
                : FundsAvailabilityStatus.NOT_APPLICABLE).currency(currency).asOf(NOW)
            .version(10L).createTime(NOW.minusSeconds(60)).updateTime(NOW).build();
        var payment = !paid ? null : Payment.builder().intentStatus(PaymentIntentStatus.SUCCEEDED)
            .lastAttemptId(orderId).lastAttemptStatus(PaymentAttemptStatus.SUCCEEDED).successfulAttemptId(orderId)
            .capturedAmount(decimal("50")).currency(currency).build();
        // 插入顺序刻意与 sortNo 相反，验证实际 SQL 保持展示顺序。
        var items = List.of(item(orderId + 1, "second", 1, "1.25", "20", "0", "Second item"),
            item(orderId, "first", 0, "2.5", "10", "2", null));
        return new Fixture(order, checkout, items, refund, funds, payment, ready ? "US" : null);
    }

    /** 金额样例固定为两行各 25，第一行优惠 2；只构造持久化对象。 */
    private OrderItem item(long id, String reference, int sort, String quantity, String unit, String discount,
        String description) {
        return OrderItem.builder().orderItemId(id).orderId(orderId).clientItemId(reference).itemName(reference)
            .itemDescription(description).productTypeCode("SERVICE").taxCategoryCode("STANDARD")
            .quantity(decimal(quantity)).unitAmount(decimal(unit)).lineGrossAmount(decimal("25"))
            .lineDiscountAmount(decimal(discount)).lineNetAmount(decimal(sort == 0 ? "23" : "25"))
            .currency(currency).sortNo(sort).createTime(NOW.minusSeconds(60)).build();
    }

    /** 固定十进制输入，避免 double 精度参与 Arrange。 */
    public static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    /** 单次查询 Fixture，使用原数据库对象供测试明确调整字段。 */
    @Getter
    @Setter
    @Accessors(fluent = false)
    @AllArgsConstructor
    public static class Fixture {
        private CommerceOrder order;
        private CheckoutSession session;
        private List<OrderItem> items;
        private OrderRefundPosition refund;
        private OrderFundsAvailabilityProjection funds;
        private Payment payment;
        private String contextCountry;
    }

    /** 测试侧补齐现有支付 DB 投影未暴露的 Attempt 字段，不改生产对象。 */
    @Getter
    @Setter
    @Accessors(fluent = false)
    @Builder
    public static class Payment {
        @Builder.Default
        private PaymentIntentStatus intentStatus = PaymentIntentStatus.REQUIRES_PAYMENT;
        private Long lastAttemptId;
        private PaymentAttemptStatus lastAttemptStatus;
        private Long successfulAttemptId;
        @Builder.Default
        private BigDecimal capturedAmount = BigDecimal.ZERO;
        @Builder.Default
        private String currency = "USD";
        @Builder.Default
        private Instant updateTime = NOW;
    }
}
