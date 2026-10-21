package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对一个银行账户的无 Body 运营命令（申请关闭）。 */
@Getter
@AllArgsConstructor
public class CashAccountCommandRequest {
    /** 操作人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 银行账户主键 */
    @NotNull
    private final Long cashAccountId;
}
