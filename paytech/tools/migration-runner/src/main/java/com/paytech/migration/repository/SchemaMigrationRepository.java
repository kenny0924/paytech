package com.paytech.migration.repository;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.AppliedSeed;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 一个服务库连接上的迁移记录表 {@code t_schema_migration}、{@code t_schema_seed} 与迁移互斥锁。
 *
 * <p>
 * 实现绑定单个 JDBC 连接，不打开或提交事务；事务由调用方在连接上控制。
 *
 * @author Claude
 */
public interface SchemaMigrationRepository {

    /**
     * 当前连接选中的库名。
     *
     * @return {@code DATABASE()}；URL 未选库时为 null
     * @throws SQLException 数据库错误
     */
    String currentDatabase() throws SQLException;

    /**
     * 创建两张记录表（已存在则不变）；这是执行器唯一允许创建的表。
     *
     * @throws SQLException 数据库错误
     */
    void createRecordTablesIfAbsent() throws SQLException;

    /**
     * 读取全部迁移记录；记录表不存在时返回空，不创建表。
     *
     * @return 按版本升序
     * @throws SQLException 数据库错误
     */
    List<AppliedMigration> findAppliedMigrations() throws SQLException;

    /**
     * 写入一条迁移记录；{@code installed_at} 取数据库 {@code UTC_TIMESTAMP(6)}。
     *
     * @param migration 记录（installedAt 被忽略）
     * @throws SQLException 数据库错误，含同版本已存在
     */
    void insertMigration(AppliedMigration migration) throws SQLException;

    /**
     * 按文件名读取初始化数据记录；记录表不存在时返回空。
     *
     * @param seedFile 文件名
     * @return 记录
     * @throws SQLException 数据库错误
     */
    Optional<AppliedSeed> findSeed(String seedFile) throws SQLException;

    /**
     * 写入一条初始化数据记录；{@code installed_at} 取数据库 {@code UTC_TIMESTAMP(6)}。
     *
     * @param seedFile 文件名
     * @param checksum 校验和
     * @throws SQLException 数据库错误，含同文件已存在
     */
    void insertSeed(String seedFile, String checksum) throws SQLException;

    /**
     * 以 {@code GET_LOCK} 获取会话级命名锁，串行化同一库的多个执行器实例。
     *
     * @param lockName 锁名，不超过 64 个字符
     * @param timeoutSeconds 等待秒数
     * @return 获取成功返回 true；超时返回 false
     * @throws SQLException 数据库错误
     */
    boolean acquireLock(String lockName, int timeoutSeconds) throws SQLException;

    /**
     * 释放命名锁；连接关闭时数据库也会自动释放。
     *
     * @param lockName 锁名
     * @throws SQLException 数据库错误
     */
    void releaseLock(String lockName) throws SQLException;
}
