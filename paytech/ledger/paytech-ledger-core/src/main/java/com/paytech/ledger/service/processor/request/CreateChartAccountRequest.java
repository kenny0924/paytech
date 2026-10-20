package com.paytech.ledger.service.processor.request;

import com.paytech.ledger.core.enums.AccountClass;
import com.paytech.ledger.core.enums.NormalSide;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 在科目表下新增科目。 */
@Getter
@Setter
public class CreateChartAccountRequest {
    /** 科目表代码 */
    @NotBlank
    private String chartCode;

    /** 科目编号 */
    @NotBlank
    private String accountNo;

    /** 科目代码 */
    @NotBlank
    private String accountCode;

    /** 科目名称 */
    @NotBlank
    private String accountName;

    /** 科目类别 */
    @NotNull
    private AccountClass accountClass;

    /** 正常余额方向 */
    @NotNull
    private NormalSide normalSide;

    /** 是否末级可记账 */
    @NotNull
    private Boolean postingAllowed;

    /** 上级科目编号；一级科目为空 */
    private String parentAccountNo;

    /** 操作人 Account ID */
    @NotBlank
    private String operatorId;
}
