package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 向清算账户挂入或摘除一个 MID 的命令；币种取清算账户币种。 */
@Getter
@AllArgsConstructor
public class ClearingAccountMidRequest {
    /** 清算账户主键 */
    @NotNull
    private final Long clearingAccountId;

    /** channel-connector MID 主键 */
    @NotNull
    private final Long midId;
}
