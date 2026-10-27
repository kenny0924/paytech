package com.paytech.platform.support.referencedata.service.processor.request;

import com.paytech.platform.support.api.enums.CurrencyRoundingMode;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 新建 DRAFT 币种规则的内部请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class CreateCurrencySpecRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** ISO 4217 三位大写币种代码。 */
    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "currency 必须是三位大写 ISO 4217 代码")
    private final String currency;

    /** ISO 4217 三位数字代码，保留前导零。 */
    @NotNull
    @Pattern(regexp = "[0-9]{3}", message = "numericCode 必须是三位数字")
    private final String numericCode;

    /** 英文名称，已去除首尾空白，最多 128 字符。 */
    @NotBlank
    @Size(max = 128)
    private final String name;

    /** 货币符号，最多 8 字符。 */
    @NotBlank
    @Size(max = 8)
    private final String symbol;

    /**
     * logo 文件地址
     * <li>可空；格式为 /files/v1/{fileId}，文件须为公开文件（Processor 读取文件元数据核对）。</li>
     */
    @Pattern(regexp = "^/files/v1/[1-9][0-9]{0,18}$", message = "logoFileAddress 必须是文件服务地址 /files/v1/{fileId}")
    private final String logoFileAddress;

    /** 最小货币单位位数，0..8。 */
    @NotNull
    @Min(0)
    @Max(8)
    private final Integer minorUnit;

    /** 舍入方式。 */
    @NotNull
    private final CurrencyRoundingMode roundingMode;

    /** 操作原因，已去除首尾空白，最多 500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
}
