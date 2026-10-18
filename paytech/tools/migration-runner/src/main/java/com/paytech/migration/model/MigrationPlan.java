package com.paytech.migration.model;

import java.util.List;

/**
 * 一个服务库的迁移计划与校验结果。
 *
 * @param service 服务名
 * @param database 实际连接的库名
 * @param currentVersion 已成功执行的最高版本；未执行过为 0
 * @param targetVersion 目标版本
 * @param pending 待执行脚本，按版本升序；存在校验失败时不得执行
 * @param violations 校验失败
 * @author Claude
 */
public record MigrationPlan(String service, String database, int currentVersion, int targetVersion,
    List<MigrationScript> pending, List<Violation> violations) {

    /**
     * 是否通过校验。
     *
     * @return 无失败返回 true
     */
    public boolean isValid() {
        return violations.isEmpty();
    }
}
