package com.paytech.finance.accounting.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 提交自动记账规则版本待复核。 */
@Getter
@Setter
public class SubmitRuleVersionRequest {
    /** 规则集代码 */
    @NotBlank
    private String bundleCode;

    /** 版本号 */
    @NotNull
    private Long bundleVer;

    /** 提交人，必须等于编制人 */
    @NotNull
    private Long makerId;
}
