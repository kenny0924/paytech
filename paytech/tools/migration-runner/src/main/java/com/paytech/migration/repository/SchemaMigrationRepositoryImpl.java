package com.paytech.migration.repository;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.AppliedSeed;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 纯 JDBC 实现。表结构遵守 DATABASE_DESIGN_GUIDE：{@code t_} 前缀、完整中文注释、{@code DATETIME(6)} UTC、无 CHECK 与外键。
 *
 * @author Claude
 */
public class SchemaMigrationRepositoryImpl implements SchemaMigrationRepository {

    /**
     * 迁移记录表名。
     */
    public static final String MIGRATION_TABLE = "t_schema_migration";

    /**
     * 初始化数据记录表名。
     */
    public static final String SEED_TABLE = "t_schema_seed";

    private static final String CREATE_MIGRATION_TABLE = """
        CREATE TABLE IF NOT EXISTS `t_schema_migration` (
          `version` INT NOT NULL COMMENT 'Schema 版本号，与实例清单 versions.schema.<service> 同义；从 1 起连续。',
          `description` VARCHAR(200) NOT NULL COMMENT '脚本文件名中的 kebab-case 描述。',
          `script` VARCHAR(255) NOT NULL COMMENT '脚本文件名，格式 V<version>__<kebab-description>.sql。',
          `checksum` CHAR(64) NOT NULL COMMENT '脚本全文去 BOM、换行按 LF 归一化后 UTF-8 字节的 SHA-256 小写十六进制。',
          `installed_by` VARCHAR(100) NOT NULL COMMENT '执行该版本的数据库账号。',
          `installed_at` DATETIME(6) NOT NULL COMMENT '该版本执行结束（成功或失败）的 UTC 微秒时间。',
          `execution_ms` INT NOT NULL COMMENT '该版本全部已执行语句的耗时，单位毫秒。',
          `success` TINYINT(1) NOT NULL COMMENT '执行结果。允许值：1 全部语句成功；0 某条语句失败，执行器已停止，需人工恢复后删除本行再迁移。',
          `error_message` VARCHAR(1000) NULL COMMENT '失败语句序号、起始行号、SQLState 与数据库错误摘要；success=1 时为 NULL。',
          PRIMARY KEY (`version`)
        ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='Schema 版本迁移执行记录；由 paytech-migration-runner 创建与维护，业务代码不得读写'""";

    private static final String CREATE_SEED_TABLE = """
        CREATE TABLE IF NOT EXISTS `t_schema_seed` (
          `seed_file` VARCHAR(255) NOT NULL COMMENT '初始化数据文件名，格式 <service>-initial-data.sql。',
          `checksum` CHAR(64) NOT NULL COMMENT '文件全文去 BOM、换行按 LF 归一化后 UTF-8 字节的 SHA-256 小写十六进制。',
          `installed_at` DATETIME(6) NOT NULL COMMENT '初始化数据与本行在同一事务提交的 UTC 微秒时间。',
          PRIMARY KEY (`seed_file`)
        ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='服务初始化数据的一次性执行记录；由 paytech-migration-runner 创建与维护，业务代码不得读写'""";

    private static final String SELECT_MIGRATIONS = "SELECT version, description, script, checksum, installed_by, "
        + "installed_at, execution_ms, success, error_message FROM t_schema_migration ORDER BY version";

    private static final String INSERT_MIGRATION = "INSERT INTO t_schema_migration (version, description, script, "
        + "checksum, installed_by, installed_at, execution_ms, success, error_message) "
        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(6), ?, ?, ?)";

    private static final String TABLE_EXISTS = "SELECT COUNT(*) FROM information_schema.TABLES "
        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final Connection connection;

    /**
     * 绑定连接。
     *
     * @param connection 服务库连接；由调用方关闭
     */
    public SchemaMigrationRepositoryImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public String currentDatabase() throws SQLException {
        return queryString("SELECT DATABASE()");
    }

    @Override
    public void createRecordTablesIfAbsent() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_MIGRATION_TABLE);
            statement.execute(CREATE_SEED_TABLE);
        }
    }

    @Override
    public List<AppliedMigration> findAppliedMigrations() throws SQLException {
        if (!tableExists(MIGRATION_TABLE)) {
            return List.of();
        }
        List<AppliedMigration> migrations = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery(SELECT_MIGRATIONS)) {
            while (rows.next()) {
                migrations.add(new AppliedMigration(rows.getInt("version"), rows.getString("description"),
                    rows.getString("script"), rows.getString("checksum"), rows.getString("installed_by"),
                    rows.getObject("installed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    rows.getInt("execution_ms"), rows.getBoolean("success"), rows.getString("error_message")));
            }
        }
        return List.copyOf(migrations);
    }

    @Override
    public void insertMigration(AppliedMigration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MIGRATION)) {
            statement.setInt(1, migration.getVersion());
            statement.setString(2, migration.getDescription());
            statement.setString(3, migration.getScript());
            statement.setString(4, migration.getChecksum());
            statement.setString(5, migration.getInstalledBy());
            statement.setInt(6, migration.getExecutionMs());
            statement.setBoolean(7, migration.isSuccess());
            statement.setString(8, truncate(migration.getErrorMessage()));
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<AppliedSeed> findSeed(String seedFile) throws SQLException {
        if (!tableExists(SEED_TABLE)) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT seed_file, checksum, installed_at FROM t_schema_seed WHERE seed_file = ?")) {
            statement.setString(1, seedFile);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AppliedSeed(rows.getString("seed_file"), rows.getString("checksum"),
                    rows.getObject("installed_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
            }
        }
    }

    @Override
    public void insertSeed(String seedFile, String checksum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO t_schema_seed (seed_file, checksum, installed_at) VALUES (?, ?, UTC_TIMESTAMP(6))")) {
            statement.setString(1, seedFile);
            statement.setString(2, checksum);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean acquireLock(String lockName, int timeoutSeconds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            statement.setString(1, lockName);
            statement.setInt(2, timeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) == 1;
            }
        }
    }

    @Override
    public void releaseLock(String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            statement.executeQuery().close();
        }
    }

    /**
     * 当前库中是否存在指定表。
     *
     * @param table 表名
     * @return 存在返回 true
     * @throws SQLException 数据库错误
     */
    private boolean tableExists(String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(TABLE_EXISTS)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getInt(1) > 0;
            }
        }
    }

    /**
     * 执行只返回一个字符串的查询。
     *
     * @param sql 查询
     * @return 第一行第一列
     * @throws SQLException 数据库错误
     */
    private String queryString(String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    /**
     * 把失败摘要截断到列长度。
     *
     * @param message 失败摘要
     * @return 不超过 1000 个字符
     */
    private static String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH - 1) + "…";
    }
}
