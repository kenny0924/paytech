package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对一条匹配建议的运营命令（确认、拒绝）。 */
@Getter
@AllArgsConstructor
public class BankMatchCommandRequest {
    /** 操作人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 银行匹配主键 */
    @NotNull
    private final Long bankMatchId;
}
