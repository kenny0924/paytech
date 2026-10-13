package com.paytech.platform.support.integration.service.processor;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

/** 外部 API 配置的本地事务模板：写入 READ_COMMITTED，详情只读 REPEATABLE_READ，均 15 秒上限。 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ExternalApiTransactions {

    private final OperationsAuditService audit;
    private final TransactionTemplate query;
    private final TransactionTemplate write;

    /** 模板初始化后不再修改。 */
    public ExternalApiTransactions(OperationsAuditService audit, PlatformTransactionManager manager) {
        this.audit = audit;
        this.query = transaction(manager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
        this.write = transaction(manager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
    }

    /** 详情的主表、字段与引用来自同一快照，避免组合出从未同时存在的配置。 */
    <T> T query(Supplier<T> operation) {
        return execute(query, operation);
    }

    /** 授权复核、行锁读取、引用校验与保存同属一个事务；事务内只访问本库。 */
    <T> T write(Supplier<T> operation) {
        try {
            return execute(write, operation);
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("外部 API 配置发生并发修改，请刷新后重试", failure);
        }
    }

    /** 在真实事务激活后登记审计观察，审计在提交后独立写入。 */
    <T> T execute(TransactionTemplate transaction, Supplier<T> operation) {
        return transaction.execute(status -> {
            audit.observeTransaction();
            return operation.get();
        });
    }

    /** 固定隔离级别与超时。 */
    TransactionTemplate transaction(PlatformTransactionManager manager, int isolation, boolean readOnly) {
        var template = new TransactionTemplate(manager);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        template.setTimeout(15);
        return template;
    }
}
