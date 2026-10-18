package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.test.dal.CheckoutPaymentTestMapper;
import com.paytech.acquiring.test.dal.OrderCancellationTestMapper;
import com.paytech.acquiring.test.dal.OrderQueryTestMapper;
import com.paytech.acquiring.test.dal.RefundCreationTestMapper;

import lombok.AllArgsConstructor;

/** 独立读取数据库中的取消事实和不应被取消操作改动的业务记录。 */
@TestComponent
@AllArgsConstructor
public class OrderCancellationChecker {
    private final OrderQueryTestMapper orders;
    private final RefundCreationTestMapper refunds;
    private final CheckoutPaymentTestMapper payments;
    private final OrderCancellationTestMapper cancellations;

    /** 订单必须真实存在，不能让缺行的字段断言静默通过。 */
    public CommerceOrder order(long orderId) {
        var row = cancellations.order(orderId);
        assertNotNull(row, "取消测试订单必须存在");
        return row;
    }

    /** Direct Pay 允许没有 Session，由用例显式断言。 */
    public CheckoutSession session(long orderId) {
        return cancellations.session(orderId);
    }

    /** 检查原支付投影或恢复出的唯一 Intent。 */
    public OrderPaymentProjection payment(long orderId) {
        return payments.payment(orderId);
    }

    /** 取消终态必须同时落到订单及实际关联会话。 */
    public void assertCancelled(long orderId, boolean hasSession, Instant cancelledAt) {
        var order = order(orderId);
        assertAll(() -> assertEquals(OrderStatus.CANCELLED, order.getOrderStatus()),
            () -> assertEquals(cancelledAt, order.getCancelledAt()),
            () -> assertEquals(cancelledAt, order.getUpdateTime()), () -> assertNull(order.getPaidAt()),
            () -> assertNotNull(order.getTerminationRequestedAt()));
        var session = session(orderId);
        if (hasSession) {
            assertNotNull(session);
            assertAll(() -> assertEquals(CheckoutSessionStatus.CANCELLED, session.getSessionStatus()),
                () -> assertEquals(cancelledAt, session.getCancelledAt()),
                () -> assertEquals(cancelledAt, session.getUpdateTime()));
        } else {
            assertNull(session);
        }
    }

    /** 商品、额度、资金、上下文、接受和网络证据保持原样，不能新建退款或补偿任务。 */
    public List<List<Map<String, Object>>> unchangedFacts(long orderId) {
        return List.of(orders.itemRows(orderId), orders.refundRows(orderId), orders.fundsRows(orderId),
            orders.contextRows(orderId), cancellations.acceptances(orderId), cancellations.evidence(orderId),
            refunds.refundRows(orderId), refunds.taskRows(orderId));
    }
}
