package com.paytech.commercial.api.agreement.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 开通项的一个接受条件：同维度多值按 OR，异维度按 AND。
 * <li>维度必须在该可开通项的可配维度内；CURRENCY 为必填维度。</li>
 */
@Getter
@Setter
public class CapabilityConditionEntry {
    /** 维度代码（PricingDimension，须 acceptanceAllowed），如 CURRENCY、CARD_BRAND */
    @NotBlank(message = "dimensionCode 必填")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "dimensionCode 必须是大写稳定代码")
    private String dimensionCode;

    /**
     * 取值，至少一个且不得重复
     * <li>CURRENCY 为 ISO 4217 三位大写，国家类维度为 ISO 3166-1 alpha-2，其他为 [A-Z0-9_]{1,64}。</li>
     */
    @NotEmpty(message = "values 至少一个")
    @Size(max = 50, message = "values 最多 50 个")
    private List<@NotBlank(message = "values 不得含空值") String> values;
}
