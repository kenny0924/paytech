package com.paytech.platform.support.referencedata.service.processor.request;

import com.paytech.platform.support.api.enums.CurrencySpecStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 切换币种规则状态的内部请求，字段约束由公共 Processor 触发。 */
@Getter
@AllArgsConstructor
public class ChangeCurrencySpecStatusRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** Path 中的 ISO 4217 三位大写币种代码。 */
    @NotNull
    @Pattern(regexp = "[A-Z]{3}", message = "currency 必须是三位大写 ISO 4217 代码")
    private final String currency;

    /** 目标状态。 */
    @NotNull
    private final CurrencySpecStatus status;

    /** 调用方读取的原版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因，已去除首尾空白，最多 500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
}
