package com.paytech.migration.service;

import com.paytech.migration.model.DatabaseCredentials;
import com.paytech.migration.model.MigrationTarget;
import com.paytech.migration.model.ServiceReport;

/**
 * 在一个服务库上执行 plan、validate、migrate、seed 四个子命令。
 *
 * <p>
 * 每次调用打开并关闭一个连接；连接后 {@code DATABASE()} 必须等于 {@link MigrationTarget#expectedDatabase()}，否则抛出
 * {@link RunnerConfigurationException}。数据库执行失败不抛出，以 {@link ServiceReport#failure()} 返回。
 *
 * @author Claude
 */
public interface MigrationExecutor {

    /**
     * 计算迁移计划；只读，不创建记录表。
     *
     * @param target 迁移目标
     * @param credentials 连接信息
     * @return 结果
     */
    ServiceReport plan(MigrationTarget target, DatabaseCredentials credentials);

    /**
     * 校验目录与已执行迁移、已执行初始化数据的校验和；只读，不创建记录表。
     *
     * @param target 迁移目标
     * @param credentials 连接信息
     * @return 结果
     */
    ServiceReport validate(MigrationTarget target, DatabaseCredentials credentials);

    /**
     * 在 {@code GET_LOCK} 互斥下按版本顺序逐文件、逐语句执行待执行脚本，每个版本成功或失败都写一行记录。
     *
     * <p>
     * DDL 不可事务：某条语句失败即停止，已执行语句不回滚，记录 {@code success=0}；取锁后重新计算计划，因此并发的第二个
     * 实例在第一个完成后只会看到无待执行版本。
     *
     * @param target 迁移目标
     * @param credentials 连接信息
     * @return 结果
     */
    ServiceReport migrate(MigrationTarget target, DatabaseCredentials credentials);

    /**
     * 在迁移到目标版本后，于一个事务内首次且仅一次执行初始化数据并记录；已记录时只校验校验和。
     *
     * @param target 迁移目标
     * @param credentials 连接信息
     * @return 结果
     */
    ServiceReport seed(MigrationTarget target, DatabaseCredentials credentials);
}
