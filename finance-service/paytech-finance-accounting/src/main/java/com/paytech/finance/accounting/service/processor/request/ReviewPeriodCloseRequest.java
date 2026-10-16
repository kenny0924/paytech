package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 复核期间关账（批准或驳回共用）。 */
@Getter
@Setter
public class ReviewPeriodCloseRequest {
    /** 会计期间主键 */
    @NotNull
    private Long periodId;

    /** 期间内关账序号 */
    @NotNull
    private Integer closeNo;

    /** 复核人 */
    @NotNull
    private Long checkerId;

    /** 复核人确认的关账摘要 */
    @NotBlank
    private String approvedCloseHash;
}
