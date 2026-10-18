package com.paytech.migration.model;

import java.nio.file.Path;

/**
 * 一个服务库的迁移目标。
 *
 * @param service 服务名
 * @param expectedDatabase 连接后 {@code DATABASE()} 必须等于的库名
 * @param manifestVersion 实例清单 {@code versions.schema.<service>}；目录校验（超前文件、目标文件存在）以它为准
 * @param targetVersion 本次执行的目标版本；不超过 manifestVersion，仅 {@code migrate --target} 时小于它
 * @param serviceRoot 服务目录 {@code <sql-root>/<service>}
 * @author Claude
 */
public record MigrationTarget(String service, String expectedDatabase, int manifestVersion, int targetVersion,
    Path serviceRoot) {

    /**
     * 版本化 SQL 目录。
     *
     * @return {@code <service>/db/migrations}
     */
    public Path migrationsDirectory() {
        return serviceRoot.resolve("db").resolve("migrations");
    }

    /**
     * 初始化数据目录。
     *
     * @return {@code <service>/db/seed}
     */
    public Path seedDirectory() {
        return serviceRoot.resolve("db").resolve("seed");
    }

    /**
     * 约定的唯一初始化数据文件名。
     *
     * @return {@code <service>-initial-data.sql}
     */
    public String seedFileName() {
        return service + "-initial-data.sql";
    }
}
