package com.paytech.migration.model;

import java.util.List;

/**
 * 一个服务库在一次命令中的结果。
 *
 * @param plan 迁移计划与校验结果（migrate 为取锁后重新计算的计划）
 * @param executed 本次成功执行的版本
 * @param failure 执行失败；无失败为 null
 * @param seedOutcome seed 结果；非 seed 命令或 seed 未进行到执行判断时为 null
 * @author Claude
 */
public record ServiceReport(MigrationPlan plan, List<ExecutedMigration> executed, ExecutionFailure failure,
    SeedOutcome seedOutcome) {

    /**
     * 执行失败退出码。
     */
    public static final int EXIT_EXECUTION_FAILED = 2;

    /**
     * 校验失败退出码。
     */
    public static final int EXIT_VALIDATION_FAILED = 1;

    /**
     * 只含计划的结果。
     *
     * @param plan 迁移计划
     * @return 结果
     */
    public static ServiceReport of(MigrationPlan plan) {
        return new ServiceReport(plan, List.of(), null, null);
    }

    /**
     * 本服务的退出码：执行失败 2，校验失败 1，成功 0。
     *
     * @return 退出码
     */
    public int exitCode() {
        if (failure != null) {
            return EXIT_EXECUTION_FAILED;
        }
        if (!plan.isValid()) {
            return EXIT_VALIDATION_FAILED;
        }
        return 0;
    }
}
