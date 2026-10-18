package com.paytech.consumer.test.mocker.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.consumer.test.dal.CustomerTestMapper;
import com.paytech.foundation.testsupport.api.Clear;

/** 当前测试上下文独享两个 Client 命名空间，逐例清理其 Customer 与失效任务，不清空共享开发库。 */
@TestComponent
public final class CustomerDataMocker implements Clear {
    private final long clientId = 900_000_000_000L + new SecureRandom().nextLong(100_000_000_000L) * 2;
    private final CustomerTestMapper mapper;
    private final TransactionTemplate transaction;
    private boolean reusable = true;

    /** 使用 Spring 的真实数据库事务；随机值仅作为本次运行的隔离身份。 */
    public CustomerDataMocker(CustomerTestMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
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
            mapper.checkCustomerTables();
            assertEquals("mor_consumer", mapper.databaseName(), "consumer 测试只能连接 mor_consumer");
            assertTrue(mapper.customers(clientId, otherClientId()).isEmpty(), "上例 Customer 未清理");
        } catch (RuntimeException failure) {
            throw new AssertionError(
                "请先准备 local-environment 的 127.0.0.1:13306/mor_consumer 及 consumer Schema；建表 SQL 见 paytech/consumer/paytech-consumer-test/src/test/resources/init/ddl.sql，测试不会自动建库或迁移",
                failure);
        }
    }

    /** 插入一个指定状态的 Customer。 */
    public void mockCustomer(long customerId, String customerNo, long ownerClientId, String reference, String status) {
        mapper.insertCustomer(customerId, customerNo, ownerClientId, reference, status);
    }

    /** 只清理测试环境自有数据；失败回滚，后续用例不得复用。 */
    @Override
    public void clear() {
        try {
            transaction.executeWithoutResult(status -> {
                mapper.deleteTasks(clientId, otherClientId());
                mapper.deleteCustomers(clientId, otherClientId());
            });
            assertTrue(mapper.customers(clientId, otherClientId()).isEmpty(), "清理后仍存在 Customer");
        } catch (RuntimeException | AssertionError failure) {
            reusable = false;
            throw failure;
        }
    }
}
