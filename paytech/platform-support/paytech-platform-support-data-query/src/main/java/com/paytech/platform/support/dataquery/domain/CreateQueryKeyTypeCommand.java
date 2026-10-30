package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建键类型，状态 ACTIVE、版本 1。 */
@Getter
@AllArgsConstructor
public final class CreateQueryKeyTypeCommand {

    /** 预先分配的键类型 ID。 */
    private final long keyTypeId;

    /** 编码。 */
    private final String keyTypeCode;

    /** 内容。 */
    private final QueryKeyTypeDefinition definition;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
