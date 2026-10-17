package com.paytech.platform.support.service.processor;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.platform.support.core.audit.AuditContext;

/** Console 用例共用固定事务配置，在真实事务激活后自动登记审计终态。 */
@Component
@ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public final class OperationsTransactionExecutor {
    private final AuditContext audit;
    private final TransactionTemplate query;
    private final TransactionTemplate serializableWrite;
    private final TransactionTemplate write;

    /** 绑定同库事务管理器与审计完成通知。 */
    public OperationsTransactionExecutor(AuditContext audit,
        @Qualifier("transactionManager") PlatformTransactionManager manager) {
        this.audit = audit;
        query = transaction(manager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
        serializableWrite = transaction(manager, TransactionDefinition.ISOLATION_SERIALIZABLE, false);
        write = transaction(manager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
    }

    /** 在读已提交事务中执行写入并返回业务结果。 */
    public <T> T write(Supplier<T> operation) {
        return execute(write, operation);
    }

    /** 在同一写入事务中执行无返回值操作。 */
    public void write(Runnable operation) {
        write(() -> {
            operation.run();
            return null;
        });
    }

    /** 在可重复读事务中执行一致性查询。 */
    public <T> T query(Supplier<T> operation) {
        return execute(query, operation);
    }

    /** 为确有范围一致性要求的写入提供可串行化隔离。 */
    public <T> T serializableWrite(Supplier<T> operation) {
        return execute(serializableWrite, operation);
    }

    /** 先登记实际事务的完成通知，再执行本库操作。 */
    private <T> T execute(TransactionTemplate transaction, Supplier<T> operation) {
        return transaction.execute(status -> {
            audit.observeTransaction();
            return operation.get();
        });
    }

    /** 固定事务隔离、只读标记与有限超时。 */
    private TransactionTemplate transaction(PlatformTransactionManager manager, int isolation, boolean readOnly) {
        TransactionTemplate transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(isolation);
        transaction.setReadOnly(readOnly);
        transaction.setTimeout(15);
        return transaction;
    }
}
