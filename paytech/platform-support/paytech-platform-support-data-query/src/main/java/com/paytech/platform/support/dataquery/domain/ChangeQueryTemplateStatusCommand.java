package com.paytech.platform.support.dataquery.domain;

import java.time.Instant;

import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按原版本启用或停用模板。 */
@Getter
@AllArgsConstructor
public final class ChangeQueryTemplateStatusCommand {

    /** 模板 ID。 */
    private final long templateId;

    /** 调用方读取的模板原版本。 */
    private final int expectedVersion;

    /** 目标状态。 */
    private final QueryTemplateStatus targetStatus;

    /** 操作人账号 ID。 */
    private final long actorAccountId;

    /** 本次写入时间。 */
    private final Instant now;
}
