package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 作废自动记账规则草稿。 */
@Getter
@Setter
public class VoidRuleVersionRequest {
    /** 规则集代码 */
    @NotBlank
    private String bundleCode;

    /** 版本号 */
    @NotNull
    private Long bundleVer;

    /** 操作人，必须等于编制人 */
    @NotNull
    private Long makerId;
}
