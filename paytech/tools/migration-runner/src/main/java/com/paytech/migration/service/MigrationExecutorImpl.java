package com.paytech.migration.service;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.DatabaseCredentials;
import com.paytech.migration.model.ExecutedMigration;
import com.paytech.migration.model.ExecutionFailure;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationScript;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.SeedOutcome;
import com.paytech.migration.model.ServiceReport;
import com.paytech.migration.model.SqlStatement;
import com.paytech.migration.model.Violation;
import com.paytech.migration.repository.SchemaMigrationRepository;
import com.paytech.migration.repository.SchemaMigrationRepositoryImpl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * 纯 JDBC 执行器。迁移与初始化数据共用一把按库命名的 {@code GET_LOCK}，等待 {@value #LOCK_TIMEOUT_SECONDS} 秒。
 *
 * @author Claude
 */
public class MigrationExecutorImpl implements MigrationExecutor {

    /**
     * 迁移锁名前缀，后接库名。
     */
    public static final String LOCK_PREFIX = "paytech_schema_migration_";

    /**
     * 取锁等待秒数。
     */
    public static final int LOCK_TIMEOUT_SECONDS = 30;

    private static final int MAX_LOCK_NAME_LENGTH = 64;

    private final MigrationPlanner migrationPlanner;

    private final SeedScriptInspector seedScriptInspector;

    /**
     * 构造执行器。
     *
     * @param migrationPlanner 计划器
     * @param seedScriptInspector 初始化数据检查
     */
    public MigrationExecutorImpl(MigrationPlanner migrationPlanner, SeedScriptInspector seedScriptInspector) {
        this.migrationPlanner = migrationPlanner;
        this.seedScriptInspector = seedScriptInspector;
    }

    @Override
    public ServiceReport plan(MigrationTarget target, DatabaseCredentials credentials) {
        return onConnection(target, credentials, false, (connection, repository, database) ->
            ServiceReport.of(migrationPlanner.plan(target, database, repository.findAppliedMigrations())));
    }

    @Override
    public ServiceReport validate(MigrationTarget target, DatabaseCredentials credentials) {
        return onConnection(target, credentials, false, (connection, repository, database) -> {
            MigrationPlan plan = migrationPlanner.plan(target, database, repository.findAppliedMigrations());
            List<Violation> violations = new ArrayList<>(plan.violations());
            violations.addAll(seedScriptInspector.recordedSeedViolations(target, repository));
            return ServiceReport.of(withViolations(plan, violations));
        });
    }

    @Override
    public ServiceReport migrate(MigrationTarget target, DatabaseCredentials credentials) {
        return onConnection(target, credentials, true, (connection, repository, database) -> {
            repository.createRecordTablesIfAbsent();
            MigrationPlan plan = migrationPlanner.plan(target, database, repository.findAppliedMigrations());
            if (!plan.isValid()) {
                return ServiceReport.of(plan);
            }
            List<ExecutedMigration> executed = new ArrayList<>();
            for (MigrationScript script : plan.pending()) {
                ExecutionFailure failure = applyScript(connection, repository, script, credentials.user(), executed);
                if (failure != null) {
                    return new ServiceReport(plan, List.copyOf(executed), failure, null);
                }
            }
            return new ServiceReport(plan, List.copyOf(executed), null, null);
        });
    }

    @Override
    public ServiceReport seed(MigrationTarget target, DatabaseCredentials credentials) {
        return onConnection(target, credentials, true, (connection, repository, database) -> {
            repository.createRecordTablesIfAbsent();
            MigrationPlan plan = migrationPlanner.plan(target, database, repository.findAppliedMigrations());
            SeedScriptInspector.Inspection inspection = seedScriptInspector.inspect(target, plan, repository);
            List<Violation> violations = new ArrayList<>(plan.violations());
            violations.addAll(inspection.violations());
            MigrationPlan checked = withViolations(plan, violations);
            if (!checked.isValid() || inspection.outcome() != null) {
                return new ServiceReport(checked, List.of(), null, inspection.outcome());
            }
            ExecutionFailure failure = applySeed(connection, repository, target.seedFileName(),
                inspection.checksum(), inspection.statements());
            return new ServiceReport(checked, List.of(), failure, failure == null ? SeedOutcome.APPLIED : null);
        });
    }

    /**
     * 打开连接、核对库名、按需持锁执行工作并关闭连接；工作中的数据库错误转为执行失败。
     *
     * @param target 迁移目标
     * @param credentials 连接信息
     * @param locked 是否持迁移锁
     * @param work 工作
     * @return 结果
     */
    ServiceReport onConnection(MigrationTarget target, DatabaseCredentials credentials, boolean locked,
        ConnectionWork work) {
        try (Connection connection = open(credentials)) {
            SchemaMigrationRepository repository = new SchemaMigrationRepositoryImpl(connection);
            String database = verifiedDatabase(target, repository);
            try {
                return locked ? withLock(connection, repository, database, target, work)
                    : work.run(connection, repository, database);
            } catch (SQLException exception) {
                return failureReport(target, database, exception);
            }
        } catch (SQLException exception) {
            throw new RunnerConfigurationException(target.service() + " 数据库连接失败: " + summarize(exception));
        }
    }

    /**
     * 在迁移锁内执行工作，结束后释放锁；取锁超时返回执行失败。
     *
     * @param connection 连接
     * @param repository 记录表
     * @param database 库名
     * @param target 迁移目标
     * @param work 工作
     * @return 结果
     * @throws SQLException 数据库错误
     */
    ServiceReport withLock(Connection connection, SchemaMigrationRepository repository, String database,
        MigrationTarget target, ConnectionWork work) throws SQLException {
        String lockName = LOCK_PREFIX + database;
        if (!repository.acquireLock(lockName, LOCK_TIMEOUT_SECONDS)) {
            return new ServiceReport(emptyPlan(target, database), List.of(), new ExecutionFailure(null, null, 0, 0,
                null, LOCK_TIMEOUT_SECONDS + " 秒内未取得迁移锁 " + lockName + "，另一个执行器实例可能仍在运行"), null);
        }
        try {
            return work.run(connection, repository, database);
        } finally {
            repository.releaseLock(lockName);
        }
    }

    /**
     * 逐语句执行一个脚本并写记录；失败时同样写一行 {@code success=0} 的记录。
     *
     * @param connection 连接（autocommit）
     * @param repository 记录表
     * @param script 脚本
     * @param installedBy 执行账号
     * @param executed 成功版本收集
     * @return 失败；成功为 null
     * @throws SQLException 写记录失败
     */
    ExecutionFailure applyScript(Connection connection, SchemaMigrationRepository repository, MigrationScript script,
        String installedBy, List<ExecutedMigration> executed) throws SQLException {
        long startedAt = System.nanoTime();
        for (SqlStatement statement : script.statements()) {
            try {
                execute(connection, statement);
            } catch (SQLException exception) {
                ExecutionFailure failure = statementFailure(script.version(), script.script(), statement, exception);
                repository.insertMigration(record(script, installedBy, elapsedMillis(startedAt), failure.message()));
                return failure;
            }
        }
        int executionMs = elapsedMillis(startedAt);
        repository.insertMigration(record(script, installedBy, executionMs, null));
        executed.add(new ExecutedMigration(script.version(), script.script(), executionMs));
        return null;
    }

    /**
     * 在一个事务内执行初始化数据并写记录；任一语句失败即回滚，不写记录。
     *
     * @param connection 连接
     * @param repository 记录表
     * @param seedFile 文件名
     * @param checksum 校验和
     * @param statements 语句
     * @return 失败；成功为 null
     * @throws SQLException 写记录或提交失败（已回滚）
     */
    ExecutionFailure applySeed(Connection connection, SchemaMigrationRepository repository, String seedFile,
        String checksum, List<SqlStatement> statements) throws SQLException {
        connection.setAutoCommit(false);
        try {
            for (SqlStatement statement : statements) {
                try {
                    execute(connection, statement);
                } catch (SQLException exception) {
                    connection.rollback();
                    return statementFailure(null, seedFile, statement, exception);
                }
            }
            repository.insertSeed(seedFile, checksum);
            connection.commit();
            return null;
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * 执行一条语句，丢弃可能的结果集。
     *
     * @param connection 连接
     * @param statement 语句
     * @throws SQLException 数据库错误
     */
    static void execute(Connection connection, SqlStatement statement) throws SQLException {
        try (Statement jdbcStatement = connection.createStatement()) {
            jdbcStatement.execute(statement.sql());
        }
    }

    /**
     * 打开连接；连接参数只取自环境变量提供的 URL、账号与密码。
     *
     * @param credentials 连接信息
     * @return autocommit 连接
     * @throws SQLException 连接失败
     */
    static Connection open(DatabaseCredentials credentials) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", credentials.user());
        properties.setProperty("password", credentials.password());
        Connection connection = DriverManager.getConnection(credentials.url(), properties);
        connection.setAutoCommit(true);
        return connection;
    }

    /**
     * 核对连接选中的库名等于预期库名，并检查锁名长度。
     *
     * @param target 迁移目标
     * @param repository 记录表
     * @return 库名
     * @throws SQLException 数据库错误
     */
    static String verifiedDatabase(MigrationTarget target, SchemaMigrationRepository repository) throws SQLException {
        String database = repository.currentDatabase();
        if (database == null) {
            throw new RunnerConfigurationException(target.service() + " 的 JDBC URL 未选择数据库，预期 "
                + target.expectedDatabase());
        }
        if (!database.equals(target.expectedDatabase())) {
            throw new RunnerConfigurationException(target.service() + " 连接到的库为 " + database + "，预期 "
                + target.expectedDatabase() + "；拒绝在非本服务库上执行");
        }
        if ((LOCK_PREFIX + database).length() > MAX_LOCK_NAME_LENGTH) {
            throw new RunnerConfigurationException("库名过长，迁移锁名超过 64 个字符: " + database);
        }
        return database;
    }

    /**
     * 构造一行迁移记录。
     *
     * @param script 脚本
     * @param installedBy 执行账号
     * @param executionMs 耗时
     * @param errorMessage 失败摘要；成功为 null
     * @return 记录
     */
    static AppliedMigration record(MigrationScript script, String installedBy, int executionMs, String errorMessage) {
        return new AppliedMigration(script.version(), script.description(), script.script(), script.checksum(),
            installedBy, null, executionMs, errorMessage == null, errorMessage);
    }

    /**
     * 构造语句失败。
     *
     * @param version 版本号；初始化数据为 null
     * @param script 文件名
     * @param statement 失败语句
     * @param exception 数据库错误
     * @return 失败
     */
    static ExecutionFailure statementFailure(Integer version, String script, SqlStatement statement,
        SQLException exception) {
        String message = "第 " + statement.index() + " 条语句（第 " + statement.line() + " 行）失败: "
            + summarize(exception);
        return new ExecutionFailure(version, script, statement.index(), statement.line(), exception.getSQLState(),
            message);
    }

    /**
     * 记录表读写等非语句数据库错误的结果。
     *
     * @param target 迁移目标
     * @param database 库名
     * @param exception 数据库错误
     * @return 结果
     */
    static ServiceReport failureReport(MigrationTarget target, String database, SQLException exception) {
        return new ServiceReport(emptyPlan(target, database), List.of(), new ExecutionFailure(null, null, 0, 0,
            exception.getSQLState(), "迁移记录读写失败: " + summarize(exception)), null);
    }

    /**
     * 未能计算计划时的占位计划。
     *
     * @param target 迁移目标
     * @param database 库名
     * @return 无待执行、无校验失败、当前版本记为 0 的计划
     */
    static MigrationPlan emptyPlan(MigrationTarget target, String database) {
        return new MigrationPlan(target.service(), database, 0, target.targetVersion(), List.of(), List.of());
    }

    /**
     * 用新的校验失败替换计划中的校验失败。
     *
     * @param plan 计划
     * @param violations 校验失败
     * @return 新计划
     */
    static MigrationPlan withViolations(MigrationPlan plan, List<Violation> violations) {
        return new MigrationPlan(plan.service(), plan.database(), plan.currentVersion(), plan.targetVersion(),
            plan.pending(), List.copyOf(violations));
    }

    /**
     * 数据库错误摘要。
     *
     * @param exception 数据库错误
     * @return {@code [SQLState/错误码] 消息}
     */
    static String summarize(SQLException exception) {
        return "[" + exception.getSQLState() + "/" + exception.getErrorCode() + "] " + exception.getMessage();
    }

    /**
     * 自起点起经过的毫秒数。
     *
     * @param startedAt {@link System#nanoTime()} 起点
     * @return 毫秒
     */
    static int elapsedMillis(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
    }

    /**
     * 一个已核对库名的连接上的工作。
     */
    @FunctionalInterface
    interface ConnectionWork {

        /**
         * 执行工作。
         *
         * @param connection 连接
         * @param repository 记录表
         * @param database 库名
         * @return 结果
         * @throws SQLException 数据库错误
         */
        ServiceReport run(Connection connection, SchemaMigrationRepository repository, String database)
            throws SQLException;
    }
}
