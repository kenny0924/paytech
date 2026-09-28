package com.paytech.risk.service.transaction;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Risk 本地事务的唯一显式入口。
 *
 * <p>只读事务仅用于必须由多次 SQL 组成的一致性闭包，并固定使用无排他锁的
 * {@code REPEATABLE_READ} 快照；Validate 的纯校验仍由调用方放在两次写事务之外。</p>
 */
@Component
public final class RiskTransactionExecutor {

    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public RiskTransactionExecutor(PlatformTransactionManager transactionManager) {
        PlatformTransactionManager requiredTransactionManager = Objects.requireNonNull(transactionManager,
            "transactionManager must not be null");
        this.writeTransaction = new TransactionTemplate(requiredTransactionManager);
        this.writeTransaction.setTimeout(5);
        this.readTransaction = new TransactionTemplate(requiredTransactionManager);
        this.readTransaction.setReadOnly(true);
        this.readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /**
     * 在一个无排他锁的只读一致性快照中执行多 SQL 查询。
     *
     * @param operation 事务内同步查询
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    public <T> T read(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return readTransaction.execute(status -> operation.get());
    }

    /**
     * 在一个本地短写事务中执行并返回结果。
     *
     * @param operation 事务内同步操作
     * @param <T> 操作结果类型
     * @return 操作结果
     */
    public <T> T write(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return writeTransaction.execute(status -> operation.get());
    }

    /**
     * 在一个本地短写事务中执行无返回操作。
     *
     * @param operation 事务内同步操作
     */
    public void write(Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        write(() -> {
            operation.run();
            return null;
        });
    }
}
