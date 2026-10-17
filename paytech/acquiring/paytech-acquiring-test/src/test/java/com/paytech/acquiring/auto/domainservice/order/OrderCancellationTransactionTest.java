package com.paytech.acquiring.auto.domainservice.order;

import static com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.NOW;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.acquiring.domain.order.OrderCancellationContext;
import com.paytech.acquiring.domainservice.service.order.OrderCancellationService;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Fixture;
import com.paytech.acquiring.test.builder.order.GetOrderDataBuilder.Payment;
import com.paytech.acquiring.test.checker.order.OrderCancellationChecker;
import com.paytech.acquiring.test.checker.refund.RefundQueryChecker;
import com.paytech.acquiring.test.mocker.external.PaymentTerminationMocker;
import com.paytech.acquiring.test.mocker.local.OrderCancellationDataMocker;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 取消 Service 的 REQUIRED 传播和跨 Order / Session / Payment 投影的 CAS 原子性。
 *
 * @see OrderCancellationService#cancel(OrderCancellationContext)
 * @see OrderCancellationService#requestTermination(OrderCancellationContext)
 */
class OrderCancellationTransactionTest extends MockTestBase {
    @Autowired
    private RefundQueryChecker snapshots;
    @Autowired
    private OrderQueryDataMocker orders;
    @Autowired
    private PaymentTerminationMocker terminations;
    @Autowired
    private OrderCancellationChecker checker;
    @Autowired
    private OrderCancellationService service;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private OrderCancellationDataMocker data;

    private Fixture fixture;

    /** 用例自行插入当前场景，公共生命周期继续负责隔离和清理。 */
    @BeforeEach
    void setUpClassMocks() {
        fixture = orders.template().ready().build();
    }

    /** 两个公开保存入口均加入已有事务，外层回滚不能留下任何取消写入。 */
    @Test
    void test_requiredRollsBackTerminationAndCancellation() {
        data.mock(fixture);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        var context = context();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.requestTermination(context);
            service.cancel(context);
            checker.assertCancelled(fixture.getOrder().getOrderId(), true, NOW);
            status.setRollbackOnly();
        });

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 会话最后一步 CAS 失败时回滚本次取消；之前独立提交的终止意图仍需保留。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_sessionConflictRollsBackOrder(boolean previouslyTerminating) {
        if (previouslyTerminating) {
            fixture.getOrder().setTerminationRequestedAt(NOW.minusSeconds(10));
        }
        data.mock(fixture);
        var context = context();
        data.advanceSessionVersion(fixture.getOrder().getOrderId());
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());

        assertThrows(ConcurrentRequestException.class, () -> service.cancel(context));

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** Payment 投影变化后，即便原上下文尚未支付，也不能提交取消或终止标记。 */
    @Test
    void test_paymentConflictRollsBackTermination() {
        fixture.setPayment(Payment.builder().build());
        data.mock(fixture);
        var context = context();
        data.advancePaymentVersion(fixture.getOrder().getOrderId());
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());

        assertThrows(ConcurrentRequestException.class, () -> service.cancel(context));

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 过期 Order 版本在终止意图写入时即被拒绝，不继续取消。 */
    @Test
    void test_staleOrderRejectsTermination() {
        data.mock(fixture);
        var context = context();
        context.getOrder().setVersion(context.getOrder().getVersion() - 1);
        var before = snapshots.snapshot(checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());

        assertThrows(ConcurrentRequestException.class, () -> service.requestTermination(context));

        snapshots.assertUnchanged(before, checkoutDataMocker.clientId(), checkoutDataMocker.otherClientId());
        terminations.assertNoCalls();
    }

    /** 读取真实已提交事实构造 Service 输入，不替换 Repository 或 Mapper。 */
    private OrderCancellationContext context() {
        long id = fixture.getOrder().getOrderId();
        return new OrderCancellationContext(checker.order(id), checker.session(id), checker.payment(id), false);
    }
}
