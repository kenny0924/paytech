package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改数据源；passwordToken 为 null 表示保留原密码。 */
@Getter
@AllArgsConstructor
public final class UpdateDataSourceCommand {

    /** 数据源 ID。 */
    private final long dataSourceId;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 数据源名称。 */
    private final String dataSourceName;

    /** 连接参数。 */
    private final DataSourceConnectionConfig connection;

    /** 新密码的 Vault Token；为 null 表示保留原密码。 */
    private final String passwordToken;

    /** 连接池最大连接数。 */
    private final int maxPoolSize;

    /** 说明，可为 null。 */
    private final String description;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
