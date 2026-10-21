package com.paytech.reconciliation.api.clearing.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 向清算账户挂入 MID 请求；币种取清算账户币种。 */
@Getter
@Setter
public class AddClearingAccountMidRequestBody {
    /**
     * channel-connector MID 主键
     * <li>必须 ACTIVE，渠道与经营主体等于清算账户，能力组含清算币种。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String midId;
}
