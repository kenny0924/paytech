package com.paytech.acquiring.test.mocker.local;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;

import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Payment;
import com.paytech.acquiring.test.dal.OrderExpiryTestMapper;

/** 过期场景的本地前置订单；复用订单、取消与支付 Fixture 及其全局清理，只额外设定到期时刻。 */
@TestComponent
public class OrderExpiryDataMocker {
    /** 默认到期时刻：固定测试时钟前一分钟。 */
    public static final Instant DUE_AT = NOW.minusSeconds(60);

    private final OrderExpiryTestMapper mapper;
    private final OrderQueryDataMocker orders;
    private final CheckoutPaymentDataMocker payments;
    private final OrderCancellationDataMocker cancellations;

    /** 依赖均为测试组件，准备事务在调用被测入口前提交。 */
    public OrderExpiryDataMocker(OrderExpiryTestMapper mapper, OrderQueryDataMocker orders,
        CheckoutPaymentDataMocker payments, OrderCancellationDataMocker cancellations) {
        this.mapper = mapper;
        this.orders = orders;
        this.payments = payments;
        this.cancellations = cancellations;
    }

    /** 新订单 Builder，ID 与 Client 由全局 Mocker 预留；同一用例可建多单，Client 订单号各不相同。 */
    public GetOrderDataBuilder template() {
        return orders.template().clientOrderId("expiry-" + UUID.randomUUID());
    }

    /** 按 Builder 插入订单，并把到期时刻设为指定值。 */
    public Fixture mock(GetOrderDataBuilder builder, Instant expiresAt) {
        var fixture = builder.build();
        fixture.getOrder().setExpiresAt(expiresAt);
        cancellations.mock(fixture);
        return fixture;
    }

    /** 到期、仍为 CREATED 且未关联会话的 Direct Pay 订单。 */
    public Fixture mockCreatedWithoutSession() {
        return mock(template().directPay(), DUE_AT);
    }

    /** 到期、会话 OPEN 但尚未提交支付的 Hosted Checkout 订单。 */
    public Fixture mockOpen() {
        return mock(template().ready(), DUE_AT);
    }

    /** 到期且已绑定 Intent 投影的 OPEN 订单；投影 Intent ID 等于订单 ID，状态 REQUIRES_PAYMENT。 */
    public Fixture mockWithProjection() {
        var fixture = template().ready().build();
        fixture.setPayment(Payment.builder().build());
        fixture.getOrder().setExpiresAt(DUE_AT);
        cancellations.mock(fixture);
        return fixture;
    }

    /** 到期、已保存三份接受证据但没有支付投影的订单，表示 Intent 创建响应丢失的窗口。 */
    public CreateCheckoutContext mockAcceptedWithoutProjection() {
        var context = payments.mockPrepared(false);
        long orderId = context.getOrder().getOrderId();
        cancellations.mockAcceptances(orderId, NOW);
        if (mapper.updateExpiresAt(orderId, DUE_AT) != 1) {
            throw new IllegalStateException("前置订单不存在：" + orderId);
        }
        context.getOrder().setExpiresAt(DUE_AT);
        return context;
    }
}
