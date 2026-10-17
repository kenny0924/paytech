package com.paytech.platform.support.accesscontrol.service.processor;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditService;

/** Operations 管理的本地事务策略，统一审计终态登记及数据库并发异常。 */
@Component
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class OperationsManagementTransactions {
    private final OperationsAuditService audit;
    private final TransactionTemplate query;
    private final TransactionTemplate write;

    /** 固定查询快照与本地写隔离；模板初始化后不再修改。 */
    public OperationsManagementTransactions(OperationsAuditService audit, PlatformTransactionManager manager) {
        this.audit = audit;
        query = transaction(manager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
        write = transaction(manager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
    }

    /** 授权、列表及总数来自同一只读快照。 */
    <T> T query(Supplier<T> operation) {
        return execute(query, operation);
    }

    /** 显式锁定读和 CAS 保护本地写；外部准备必须由 Processor 先完成。 */
    <T> T write(Supplier<T> operation) {
        try {
            return execute(write, operation);
        } catch (ConcurrencyFailureException failure) {
            throw new ConcurrentRequestException("Operations 管理命令发生并发冲突，请刷新后重试", failure);
        }
    }

    /** 在真实事务激活后登记审计，审计保存继续独立且尽力执行。 */
    <T> T execute(TransactionTemplate transaction, Supplier<T> operation) {
        return transaction.execute(status -> {
            audit.observeTransaction();
            return operation.get();
        });
    }

    /** 保留 15 秒上限，事务内只执行本库操作。 */
    TransactionTemplate transaction(PlatformTransactionManager manager, int isolation, boolean readOnly) {
        var template = new TransactionTemplate(manager);
        template.setIsolationLevel(isolation);
        template.setReadOnly(readOnly);
        template.setTimeout(15);
        return template;
    }
}
