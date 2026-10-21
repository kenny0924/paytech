package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对一张银行对账单的无 Body 运营命令（完成导入）。 */
@Getter
@AllArgsConstructor
public class BankStatementCommandRequest {
    /** 操作人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 银行对账单主键 */
    @NotNull
    private final Long statementId;
}
