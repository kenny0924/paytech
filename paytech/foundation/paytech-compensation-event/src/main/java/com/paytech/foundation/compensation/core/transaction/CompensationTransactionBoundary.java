package com.paytech.foundation.compensation.core.transaction;

import java.util.Objects;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;

/**
 * 服务本地 MyBatis 事务边界的失败关闭保护。
 *
 * @author Codex
 */
public final class CompensationTransactionBoundary {

    private final DataSource dataSource;
    private final TransactionTemplate mandatoryTransaction;
    private final TransactionTemplate requiredTransaction;

    public CompensationTransactionBoundary(SqlSessionFactory sqlSessionFactory,
        PlatformTransactionManager transactionManager) {
        this.dataSource = requireMyBatisDataSource(sqlSessionFactory);
        DataSourceTransactionManager dataSourceTransactionManager = requireTransactionManager(transactionManager);
        DataSource transactionDataSource = requireTransactionDataSource(dataSourceTransactionManager);
        if (dataSource != transactionDataSource) {
            throw configurationError("compensation MyBatis and transaction manager must use the same DataSource bean");
        }
        this.mandatoryTransaction = new TransactionTemplate(dataSourceTransactionManager);
        this.mandatoryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        this.requiredTransaction = new TransactionTemplate(dataSourceTransactionManager);
        this.requiredTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public <T> T executeMandatory(String operation, Supplier<T> action) {
        String requiredOperation = Objects.requireNonNull(operation, "operation must not be null");
        Supplier<T> requiredAction = Objects.requireNonNull(action, "action must not be null");
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(requiredOperation + " requires an active Spring transaction");
        }
        try {
            return mandatoryTransaction.execute(status -> requiredAction.get());
        } catch (IllegalTransactionStateException exception) {
            throw new IllegalStateException(
                requiredOperation + " requires an active transaction for the MyBatis DataSource", exception);
        }
    }

    public <T> T executeRequired(Supplier<T> action) {
        Supplier<T> requiredAction = Objects.requireNonNull(action, "action must not be null");
        return requiredTransaction.execute(status -> requiredAction.get());
    }

    private DataSource requireMyBatisDataSource(SqlSessionFactory sqlSessionFactory) {
        SqlSessionFactory requiredFactory =
            Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory must not be null");
        Environment environment = requiredFactory.getConfiguration().getEnvironment();
        if (environment == null || environment.getDataSource() == null) {
            throw configurationError("compensation MyBatis environment requires a DataSource");
        }
        return environment.getDataSource();
    }

    private DataSourceTransactionManager requireTransactionManager(PlatformTransactionManager transactionManager) {
        PlatformTransactionManager requiredManager =
            Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        if (!(requiredManager instanceof DataSourceTransactionManager dataSourceTransactionManager)) {
            throw configurationError("compensation requires a DataSourceTransactionManager or JdbcTransactionManager");
        }
        return dataSourceTransactionManager;
    }

    private DataSource requireTransactionDataSource(DataSourceTransactionManager dataSourceTransactionManager) {
        DataSource transactionDataSource = dataSourceTransactionManager.getDataSource();
        if (transactionDataSource == null) {
            throw configurationError("compensation transaction manager requires a DataSource");
        }
        return transactionDataSource;
    }

    private FailException configurationError(String message) {
        return new FailException(CompensationErrorCode.CONFIGURATION_INVALID, message);
    }
}
