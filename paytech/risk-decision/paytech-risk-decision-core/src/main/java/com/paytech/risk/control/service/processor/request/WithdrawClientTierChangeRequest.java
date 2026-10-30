package com.paytech.risk.control.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 申请人撤回档位变更申请的 Owner 输入。 */
@Getter
@AllArgsConstructor
public class WithdrawClientTierChangeRequest {
    /** 撤回人账号 ID，须是申请人。 */
    private final long actorAccountId;

    /** 申请 ID。 */
    @Positive
    private final long changeId;

    /** 期望的申请并发版本。 */
    @Positive
    private final int expectedVersion;
}
