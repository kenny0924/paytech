package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 复核自动记账规则版本（批准或驳回共用）。 */
@Getter
@Setter
public class ReviewRuleVersionRequest {
    /** 规则集代码 */
    @NotBlank
    private String bundleCode;

    /** 版本号 */
    @NotNull
    private Long bundleVer;

    /** 复核人，必须不同于编制人 */
    @NotNull
    private Long checkerId;
}
