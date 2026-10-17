package com.paytech.acquiring.service.transaction;

import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit transaction boundaries shared by Commerce application processors. */
@Component
public final class CommerceTransactionExecutor {

    private static final int TRANSACTION_TIMEOUT_SECONDS = 15;

    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public CommerceTransactionExecutor(PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        writeTransaction = template(transactionManager, TransactionDefinition.ISOLATION_READ_COMMITTED, false);
        readTransaction = template(transactionManager, TransactionDefinition.ISOLATION_REPEATABLE_READ, true);
    }

    /** Executes one atomic local write use case. */
    public <T> T write(Supplier<T> action) {
        return writeTransaction.execute(status -> Objects.requireNonNull(action, "action").get());
    }

    /** Executes a query that requires one stable database snapshot. */
    public <T> T read(Supplier<T> action) {
        return readTransaction.execute(status -> Objects.requireNonNull(action, "action").get());
    }

    private TransactionTemplate template(
        PlatformTransactionManager transactionManager,
        int isolationLevel,
        boolean readOnly
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(isolationLevel);
        template.setReadOnly(readOnly);
        template.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
        return template;
    }
}
