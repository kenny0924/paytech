package com.paytech.channel.connector.api.channel.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 能力组内一项能力明细
 * <li>三个值字段恰好提供一个，且与能力目录登记的类型一致；CODE 值须在目录允许值内。</li>
 */
@Getter
@Setter
public class MidCapabilityDetailRequestBody {
    /** 已在能力目录登记的能力代码，例如 ACTION.PAY */
    @NotBlank(message = "能力代码必填")
    @Size(max = 128, message = "能力代码最长 128 字符")
    private String capabilityCode;

    /** BOOLEAN 类型的值；false 表示明确不支持 */
    private Boolean booleanValue;

    /**
     * DECIMAL 类型的值
     * <li>普通十进制字符串，最多 16 位整数、8 位小数；金额按交易币种主单位。</li>
     */
    @Pattern(regexp = "-?(0|[1-9][0-9]{0,15})(\\.[0-9]{1,8})?", message = "数值必须是最多 8 位小数的十进制字符串")
    private String decimalValue;

    /** CODE 类型的目录值 */
    @Size(min = 1, max = 128, message = "代码值长度为 1–128 字符")
    private String codeValue;
}
