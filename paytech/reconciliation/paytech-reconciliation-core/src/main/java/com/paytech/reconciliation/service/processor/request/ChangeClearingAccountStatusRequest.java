package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;

import com.paytech.reconciliation.api.enums.ClearingAccountStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 启用或停用清算账户命令。 */
@Getter
@AllArgsConstructor
public class ChangeClearingAccountStatusRequest {
    /** 清算账户主键 */
    @NotNull
    private final Long clearingAccountId;

    /** 目标状态 */
    @NotNull
    private final ClearingAccountStatus status;
}
