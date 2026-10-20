package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 修改科目名称、编号或上级。 */
@Getter
@Setter
public class UpdateChartAccountRequest {
    /** 科目表代码 */
    @NotBlank
    private String chartCode;

    /** 科目主键 */
    @NotNull
    private Long chartAccountId;

    /** 修改后的科目名称 */
    @NotBlank
    private String accountName;

    /** 修改后的科目编号 */
    @NotBlank
    private String accountNo;

    /** 修改后的上级科目编号；为空表示一级科目 */
    private String parentAccountNo;

    /** 操作人 Account ID */
    @NotBlank
    private String operatorId;
}
