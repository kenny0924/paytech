package com.paytech.billing.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** Checker 批准计费事实。 */
@Getter
@Setter
public class ApproveBillingFactRequestBody {
    /**
     * 复核人平台用户标识
     * <li>必须不同于编制人；同一复核人重复批准返回已批准事实。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String checkerId;
}
