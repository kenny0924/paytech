package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.DataSourceStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本切换数据源状态：启用、停用或删除。 */
@Getter
@AllArgsConstructor
public final class ChangeDataSourceStatusCommand {

    /** 数据源 ID。 */
    private final long dataSourceId;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 目标状态。 */
    private final DataSourceStatus targetStatus;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
