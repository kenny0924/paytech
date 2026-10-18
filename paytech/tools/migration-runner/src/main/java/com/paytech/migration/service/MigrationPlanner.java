package com.paytech.migration.service;

import com.paytech.migration.model.AppliedMigration;
import com.paytech.migration.model.MigrationPlan;
import com.paytech.migration.model.MigrationTarget;

import java.util.List;

/**
 * 读取一个服务的版本化 SQL 目录，结合已执行记录得出迁移计划与校验结果；不访问数据库。
 *
 * @author Claude
 */
public interface MigrationPlanner {

    /**
     * 计算迁移计划。
     *
     * <p>
     * 校验目录（文件名、版本唯一且从 1 起连续、无高于目标的超前文件、目标版本有文件、脚本可读可切分且不含禁止语句）与
     * 记录（无失败记录；清单目标版本及以下的成功记录与文件的校验和、文件名一致；无中间缺口）。高于清单目标版本的成功
     * 记录视为 N 与 N-1 并存，不校验也不回退。待执行脚本只到 {@link MigrationTarget#targetVersion()}。
     *
     * @param target 迁移目标
     * @param database 实际连接的库名
     * @param applied {@code t_schema_migration} 全部记录；记录表不存在时为空
     * @return 计划；{@link MigrationPlan#isValid()} 为 false 时不得执行
     */
    MigrationPlan plan(MigrationTarget target, String database, List<AppliedMigration> applied);
}
