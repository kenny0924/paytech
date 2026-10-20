package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 停用或启用科目。 */
@Getter
@Setter
public class ChartAccountStatusRequest {
    /** 科目表代码 */
    @NotBlank
    private String chartCode;

    /** 科目主键 */
    @NotNull
    private Long chartAccountId;

    /** 操作人 Account ID */
    @NotBlank
    private String operatorId;
}
