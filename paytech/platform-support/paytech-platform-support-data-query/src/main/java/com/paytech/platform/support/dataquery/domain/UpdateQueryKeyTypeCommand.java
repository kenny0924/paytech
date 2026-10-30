package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本修改键类型内容；编码与状态不变。 */
@Getter
@AllArgsConstructor
public final class UpdateQueryKeyTypeCommand {

    /** 键类型 ID。 */
    private final long keyTypeId;

    /** 调用方读取的原版本。 */
    private final int expectedVersion;

    /** 新内容。 */
    private final QueryKeyTypeDefinition definition;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
