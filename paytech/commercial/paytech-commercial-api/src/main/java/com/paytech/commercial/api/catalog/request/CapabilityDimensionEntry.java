package com.paytech.commercial.api.catalog.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 可开通项的一个可配维度。 */
@Getter
@Setter
public class CapabilityDimensionEntry {
    /** 维度代码（PricingDimension），必须可用于开通接受条件 */
    @NotBlank(message = "dimensionCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "dimensionCode 必须是大写稳定代码")
    private String dimensionCode;

    /** 合同开通时是否必须给出该维度条件 */
    @NotNull(message = "required 必填")
    private Boolean required;
}
