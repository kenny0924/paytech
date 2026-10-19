package com.paytech.commercial.api.agreement.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 开通项约束的一个值：同码多行按 OR，异码按 AND。 */
@Getter
@Setter
public class CapabilityLimitEntry {
    /** 约束类别 */
    @NotBlank(message = "limitCode 必填")
    @Pattern(regexp = "CURRENCY|CONSUMER_COUNTRY|REQUIRE_3DS", message = "limitCode 只能为 CURRENCY、CONSUMER_COUNTRY、REQUIRE_3DS")
    private String limitCode;

    /**
     * 规范化值
     * <li>CURRENCY 为 ISO 4217 三位大写，CONSUMER_COUNTRY 为 ISO 3166-1 alpha-2，REQUIRE_3DS 固定为 1。</li>
     */
    @NotBlank(message = "limitValue 必填")
    @Size(max = 128, message = "limitValue 最长 128 字符")
    private String limitValue;
}
