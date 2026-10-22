package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建数据源；密码已在事务外保存到 Vault，这里只带 Token。 */
@Getter
@AllArgsConstructor
public final class CreateDataSourceCommand {

    /** 预先分配的数据源 ID。 */
    private final long dataSourceId;

    /** 数据源编码。 */
    private final String dataSourceCode;

    /** 数据源名称。 */
    private final String dataSourceName;

    /** 连接参数。 */
    private final DataSourceConnectionConfig connection;

    /** 密码 Vault Token。 */
    private final String passwordToken;

    /** 连接池最大连接数，已校验不超过上限。 */
    private final int maxPoolSize;

    /** 说明，可为 null。 */
    private final String description;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
