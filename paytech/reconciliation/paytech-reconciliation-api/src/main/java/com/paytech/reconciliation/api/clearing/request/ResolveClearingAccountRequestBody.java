package com.paytech.reconciliation.api.clearing.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 按 MID + 币种解析清算账户请求。 */
@Getter
@Setter
public class ResolveClearingAccountRequestBody {
    /**
     * channel-connector MID 主键
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String midId;

    /**
     * 交易币种
     * <li>大写 ISO 4217。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;
}
