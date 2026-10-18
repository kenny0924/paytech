package com.paytech.acquiring.auto.domainservice.refund;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.acquiring.domain.refund.RefundCreationContext;
import com.paytech.acquiring.domainservice.service.refund.RefundService;
import com.paytech.acquiring.test.base.MockTestBase;
import com.paytech.acquiring.test.builder.refund.CreateRefundRequestBuilder;
import com.paytech.acquiring.test.checker.refund.RefundCreationChecker;
import com.paytech.acquiring.test.dal.RefundCreationTestMapper;
import com.paytech.acquiring.test.mocker.local.OrderQueryDataMocker;
import com.paytech.acquiring.test.mocker.local.RefundCreationDataMocker;
import com.paytech.foundation.common.error.ConcurrentRequestException;

/**
 * 公开 Service 的真实事务传播与 CAS 回滚；HTTP 用例已验证无上游事务时的自动提交。
 *
 * @see RefundService#saveCreateRefundContext(RefundCreationContext)
 */
class RefundCreationTransactionTest extends MockTestBase {
    @Autowired
    private RefundService service;
    @Autowired
    private OrderQueryDataMocker orderData;
    @Autowired
    private RefundCreationDataMocker refundData;
    @Autowired
    private RefundCreationChecker checker;
    @Autowired
    private RefundCreationTestMapper mapper;
    @Autowired
    private PlatformTransactionManager transactions;
    private RefundCreationContext context;

    @BeforeEach
    void setUpClassMocks() {
        var fixture = orderData.template().paid().build();
        orderData.mock(fixture);
        context = refundData.context(fixture, CreateRefundRequestBuilder.template().build());
    }

    /** 最后一步已经登记事件，但外层回滚必须撤销退款、预占和事件，并且不能提前唤醒。 */
    @Test
    void test_requiredJoinsCallerAndRollsBackAllFacts() {
        var before = checker.snapshot(context.getOrder().getOrderId());
        String no = context.getRefund().getRefundRequestNo();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.saveCreateRefundContext(context);
            checker.assertPosition(context.getOrder().getOrderId(), "10", "0", 10);
            assertEquals(1, mapper.refunds(context.getOrder().getOrderId()).size());
            var task = mapper.task(no);
            assertNotNull(task);
            assertEquals("INIT", task.getExecuteStatus());
            assertEquals(0, task.getExecuteCount());
            status.setRollbackOnly();
        });
        checker.assertRejected(context.getOrder().getOrderId(), before);
        checker.assertNoTask(no);
    }

    /** SQL 条件写失败发生在插入退款之后，退款行必须回滚，也不能留下任务。 */
    @Test
    void test_stalePositionRollsBackInsertedRefund() {
        long orderId = context.getOrder().getOrderId();
        refundData.advancePositionVersion(orderId);
        var before = checker.snapshot(orderId);

        assertThrows(ConcurrentRequestException.class, () -> service.saveCreateRefundContext(context));

        checker.assertRejected(orderId, before);
        checker.assertPosition(orderId, "0", "0", 10);
        checker.assertNoTask(context.getRefund().getRefundRequestNo());
    }
}
