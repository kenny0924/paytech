package com.paytech.acquiring.auto.domainservice.customer;

import static com.paytech.acquiring.test.builder.customer.SaveConsumerRequestBuilder.template;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.paytech.acquiring.core.dal.dataobject.customer.ConsumerContext;
import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.ProtectedConsumer;
import com.paytech.acquiring.domain.customer.SaveConsumerContext;
import com.paytech.acquiring.domainservice.service.customer.CustomerCheckoutService;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.checker.customer.SaveConsumerChecker;
import com.paytech.acquiring.test.checker.order.OrderCancellationChecker;
import com.paytech.acquiring.test.mocker.external.ConsumerProtectionMocker;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 消费者 Service 的 REQUIRED 传播与两表原子性，使用真实事务及数据库约束。
 *
 * @see CustomerCheckoutService#saveConsumerContext(SaveConsumerContext)
 */
class SaveConsumerTransactionTest extends MockTestBase {
    @Autowired
    private CheckoutPaymentDataMocker orders;
    @Autowired
    private SaveConsumerChecker checker;
    @Autowired
    private OrderCancellationChecker orderChecker;
    @Autowired
    private CustomerCheckoutService service;
    @Autowired
    private ConsumerProtectionMocker protection;
    @Autowired
    private PlatformTransactionManager transactions;
    private CreateCheckoutContext fixture;

    /** 当前类只需已提交订单，资料保护结果由调用者作为 Context 输入提供。 */
    @BeforeEach
    void setUpConsumerTransactions() {
        fixture = orders.mockPrepared(false);
    }

    /** 没有上层事务时 Service 自建事务，两个表均提交。 */
    @Test
    void test_standaloneServiceCommitsBothTables() {
        var context = context(false);
        var before = orderChecker.order(orderId());
        var unrelated = checker.unchangedFacts(orderId());

        service.saveConsumerContext(context);

        checker.assertSaved(orderId(), context.getDetails(), context.getProtectedValues(), 1,
            adjustableUtcClock.instant(), adjustableUtcClock.instant());
        checker.assertOrderChangedOnlyVersion(before, adjustableUtcClock.instant());
        assertEquals(context.getConsumer().getConsumerContextId(), checker.consumer(orderId()).getConsumerContextId());
        assertEquals(unrelated, checker.unchangedFacts(orderId()));
        protection.assertNoCalls();
    }

    /** 首次保存和替换都加入已有事务；外层回滚应同时恢复订单和资料。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void test_requiredJoinsCallerTransaction(boolean existing) {
        if (existing) {
            fixture = orders.mockPrepared(true);
        }
        var context = context(existing);
        var before = checker.snapshot(orderId());
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.saveConsumerContext(context);
            checker.assertSaved(orderId(), context.getDetails(), context.getProtectedValues(), existing ? 2 : 1,
                adjustableUtcClock.instant(), adjustableUtcClock.instant());
            status.setRollbackOnly();
        });
        assertEquals(before, checker.snapshot(orderId()));
        protection.assertNoCalls();
    }

    /** 更新资料的最后一步 CAS 失败，已执行的订单版本递增必须回滚。 */
    @Test
    void test_consumerConflictRollsBackOrder() {
        fixture = orders.mockPrepared(true);
        var context = context(true);
        context.getConsumer().setVersion(context.getConsumer().getVersion() + 1);
        var before = checker.snapshot(orderId());

        assertThrows(ConcurrentRequestException.class, () -> service.saveConsumerContext(context));

        assertEquals(before, checker.snapshot(orderId()));
        protection.assertNoCalls();
    }

    /** 首次插入撞订单唯一约束时，不留下订单版本的部分提交。 */
    @Test
    void test_duplicateConsumerRollsBackOrder() {
        fixture = orders.mockPrepared(true);
        var context = context(false);
        var before = checker.snapshot(orderId());

        assertThrows(DuplicateKeyException.class, () -> service.saveConsumerContext(context));

        assertEquals(before, checker.snapshot(orderId()));
        protection.assertNoCalls();
    }

    /** 输入直接使用真实数据库对象；不通过生产保存方法制造前置资料。 */
    private SaveConsumerContext context(boolean existing) {
        var consumer = existing ? checker.consumer(orderId()) : new ConsumerContext();
        if (!existing) {
            consumer.setConsumerContextId(checkoutDataMocker.reserveOrderId());
            consumer.setOrderId(orderId());
        }
        return new SaveConsumerContext(orderChecker.order(orderId()), orderChecker.session(orderId()), consumer,
            template().details(), new ProtectedConsumer("service-email-reference", "service-profile-reference"));
    }

    /** 当前用例持有的订单标识。 */
    private long orderId() {
        return fixture.getOrder().getOrderId();
    }
}
