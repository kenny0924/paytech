package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryKeyTypeStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本启用或停用键类型。 */
@Getter
@AllArgsConstructor
public final class ChangeQueryKeyTypeStatusCommand {

    /** 键类型 ID。 */
    private final long keyTypeId;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 目标状态。 */
    private final QueryKeyTypeStatus targetStatus;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
