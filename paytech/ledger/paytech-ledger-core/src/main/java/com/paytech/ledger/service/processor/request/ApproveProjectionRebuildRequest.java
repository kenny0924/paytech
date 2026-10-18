package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 批准试算异常的投影重建。 */
@Getter
@Setter
public class ApproveProjectionRebuildRequest {
    /** 试算异常主键 */
    @NotNull
    private Long exceptionId;

    /** 复核人 */
    @NotNull
    private Long checkerId;
}
