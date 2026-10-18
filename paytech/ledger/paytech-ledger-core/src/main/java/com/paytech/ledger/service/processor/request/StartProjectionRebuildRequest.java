package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 以投影重建方式发起试算异常处置。 */
@Getter
@Setter
public class StartProjectionRebuildRequest {
    /** 试算异常主键 */
    @NotNull
    private Long exceptionId;

    /** 编制人 */
    @NotNull
    private Long makerId;
}
