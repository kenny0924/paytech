package com.paytech.risk.control.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审批或驳回档位变更申请的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class DecideClientTierChangeRequest {
    /** 审批人账号 ID，不能是申请人。 */
    private final long actorAccountId;

    /** 申请 ID。 */
    @Positive
    private final long changeId;

    /** 期望的申请并发版本。 */
    @Positive
    private final int expectedVersion;

    /** 意见；驳回必填。 */
    private final String comment;

    /**
     * 是否经高级审批接口进入
     * <li>普通接口（档位审批权限）只能处理 STANDARD 级别；高级接口可处理任意级别。</li>
     */
    private final boolean elevated;
}
