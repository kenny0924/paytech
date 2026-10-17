package com.paytech.commerce.auto.domainservice.checkout;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commerce.domain.checkout.CreateCheckoutContext;
import com.paytech.commerce.domainservice.service.checkout.CheckoutCreationService;
import com.paytech.commerce.test.base.MockTestBase;
import com.paytech.commerce.test.builder.checkout.CreateCheckoutContextBuilder;

/**
 * HTTP 成功场景之外，单独验证公开原子写入入口的末步回滚和 REQUIRED 传播。
 *
 * @see CheckoutCreationService#saveCreateCheckoutContext(CreateCheckoutContext)
 */
class CheckoutCreationTransactionTest extends MockTestBase {
    @Autowired
    private CheckoutCreationService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private CreateCheckoutContext context;

    /** 类公共准备仅组装本例 Context，不调用被测业务命令。 */
    @BeforeEach
    void setUpClassMocks() {
        context = CreateCheckoutContextBuilder.template().orderId(checkoutDataMocker.reserveOrderId())
            .clientId(checkoutDataMocker.clientId()).now(adjustableUtcClock.instant()).build();
    }

    /** Session 唯一键冲突，之前四类写入必须全部回滚。 */
    @Test
    void test_lastWriteFailureRollsBackAllTables() {
        long blockerId =
            checkoutDataMocker.mockSessionToken(context.getSession().getPublicToken(), adjustableUtcClock.instant());

        assertThrows(DuplicateKeyException.class, () -> service.saveCreateCheckoutContext(context));

        checkoutChecker.assertCreationRowCount(context.getOrder().getOrderId(), 0);
        checkoutChecker.assertCreationRowCount(blockerId, 2);
    }

    /** 最后的合同 INSERT 失败，已写入的订单、商业上下文及法律事实整体回滚。 */
    @Test
    void test_contractFailureRollsBackPreparation() {
        context.getFrozen().renderedContentHash(null);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> service.saveCreateCheckoutContext(context));
        checkoutChecker.assertCreationRowCount(context.getOrder().getOrderId(), 0);
    }

    /** Service 加入调用方事务，调用方回滚后不能留下一份提前提交的订单。 */
    @Test
    void test_requiredJoinsTheCallerTransaction() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.saveCreateCheckoutContext(context);
            checkoutChecker.assertCreationRowCount(context.getOrder().getOrderId(), 8);
            status.setRollbackOnly();
        });

        checkoutChecker.assertCreationRowCount(context.getOrder().getOrderId(), 0);
    }
}
