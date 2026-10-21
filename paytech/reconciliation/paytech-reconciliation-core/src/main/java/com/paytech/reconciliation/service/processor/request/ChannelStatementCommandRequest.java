package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对一张渠道账单的无 Body 运营命令（完成导入、作废）。 */
@Getter
@AllArgsConstructor
public class ChannelStatementCommandRequest {
    /** 操作人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 渠道账单主键 */
    @NotNull
    private final Long statementId;
}
