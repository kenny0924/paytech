package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;
import com.paytech.reconciliation.api.enums.ReconExceptionSourceType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 人工开立对账异常命令（technique D-02）。 */
@Getter
@AllArgsConstructor
public class OpenReconExceptionRequest {
    /** 开立人 Operations Account ID */
    @NotNull
    private final Long actorId;

    /** 来源对象类型：PLATFORM_TXN、EXPECTED_BANK_MOVEMENT、BANK_STATEMENT_LINE */
    @NotNull
    private final ReconExceptionSourceType sourceType;

    /** 来源对象主键 */
    @NotNull
    private final Long sourceId;
}
