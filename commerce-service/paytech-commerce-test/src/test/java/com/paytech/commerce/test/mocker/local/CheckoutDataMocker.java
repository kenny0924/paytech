package com.paytech.commerce.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.commerce.domainservice.port.CommerceIdentifierGenerator;
import com.paytech.commerce.test.dal.CheckoutCreationTestMapper;
import com.paytech.commerce.test.dal.CustomerTestMapper;
import com.paytech.commerce.test.dal.RefundCreationTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/** 当前测试上下文独享 Client 命名空间，逐例重建业务数据，不清空共享开发库。 */
@TestComponent
public final class CheckoutDataMocker implements Clear {
    private final long clientId = 900_000_000_000L + new SecureRandom().nextLong(100_000_000_000L) * 2;
    private final CheckoutCreationTestMapper mapper;
    private final RefundCreationTestMapper refundMapper;
    private final CustomerTestMapper customerMapper;
    private final CommerceIdentifierGenerator identifiers;
    private final TransactionTemplate transaction;
    private final Set<Long> ownedOrderIds = new HashSet<>();
    private boolean reusable = true;

    /** 使用 Spring 的真实数据库事务；随机值仅作为本次运行的隔离身份。 */
    public CheckoutDataMocker(CheckoutCreationTestMapper mapper, RefundCreationTestMapper refundMapper,
        CustomerTestMapper customerMapper, CommerceIdentifierGenerator identifiers,
        PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.refundMapper = refundMapper;
        this.customerMapper = customerMapper;
        this.identifiers = identifiers;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** 同一次测试环境所有方法使用相同 Client 基线。 */
    public long clientId() {
        return clientId;
    }

    /** 第二个 Client 用于独立命名空间的用例。 */
    public long otherClientId() {
        return clientId + 1;
    }

    /** 每例开始检查上一例已完全清理，不通过预先删除掩盖污染。 */
    public void beforeEach() {
        assertTrue(reusable, "清理失败，恢复本测试环境后重新运行");
        try {
            mapper.checkCreationColumns();
            refundMapper.checkRefundTables();
            customerMapper.checkCustomerTables();
            assertTrue(mapper.orderIds(clientId, otherClientId()).isEmpty(), "上例订单未清理");
            assertTrue(customerMapper.customers(clientId, otherClientId()).isEmpty(), "上例 Customer 未清理");
            for (long orderId : ownedOrderIds) {
                assertEquals(0, mapper.creationRows(orderId), "存在残留子行");
            }
        } catch (RuntimeException failure) {
            throw new AssertionError(
                "请先准备 local-environment 的 127.0.0.1:13306/mor_commerce 及当前 Commerce Schema（含 creation_mode、consumer_country_code、t_refund_request、t_compensation_event）；空测试 Schema 的建表 SQL 见 paytech-commerce-test/src/test/resources/init/ddl.sql，已有库的增量 SQL 见 Commerce README，测试不会自动建库或迁移",
                failure);
        }
    }

    /** 真实插入本例的已占用业务订单号，避免用第二次调用制造 Arrange。 */
    public long mockExistingOrder(String clientOrderId, String status, Instant now) {
        long orderId = reserveOrderId();
        mapper.insertOrder(orderId, "ord_fixture_" + orderId, clientId, clientOrderId, status, now.plusSeconds(1800),
            now);
        return orderId;
    }

    /** 本例的 Token 冲突事实只由测试 INSERT 准备，全局清理统一回收。 */
    public long mockSessionToken(String token, Instant now) {
        long orderId = mockExistingOrder("token-blocker", "CREATED", now);
        mapper.insertSession(orderId, token, now);
        return orderId;
    }

    /** 为真实 Service 原子性测试保留精确资源身份，回滚后也能检查和清理子行。 */
    public long reserveOrderId() {
        long orderId = identifiers.nextId();
        ownedOrderIds.add(orderId);
        return orderId;
    }

    /** 只清理测试环境全部自有数据；失败回滚，后续用例不得复用。 */
    @Override
    public void clear() {
        try {
            ownedOrderIds.addAll(mapper.orderIds(clientId, otherClientId()));
            transaction.executeWithoutResult(status -> {
                for (long orderId : ownedOrderIds) {
                    refundMapper.deleteTasks(orderId);
                    refundMapper.deleteRequests(orderId);
                    mapper.deletePayment(orderId);
                    mapper.deleteAcceptances(orderId);
                    mapper.deleteEvidence(orderId);
                    mapper.deleteConsumer(orderId);
                    mapper.deleteContract(orderId);
                    mapper.deleteLegal(orderId);
                    mapper.deleteContext(orderId);
                    mapper.deleteSession(orderId);
                    mapper.deleteFunds(orderId);
                    mapper.deleteRefund(orderId);
                    mapper.deleteItems(orderId);
                    mapper.deleteOrder(orderId);
                }
                customerMapper.deleteTasks(clientId, otherClientId());
                customerMapper.deleteCustomers(clientId, otherClientId());
                customerMapper.deleteSettings(clientId, otherClientId());
            });
            assertTrue(customerMapper.customers(clientId, otherClientId()).isEmpty(), "清理后仍存在 Customer");
            for (long orderId : ownedOrderIds) {
                assertEquals(0, mapper.creationRows(orderId), "清理后仍存在本例数据");
                assertTrue(refundMapper.refunds(orderId).isEmpty(), "清理后仍存在退款申请");
            }
            ownedOrderIds.clear();
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }
}
