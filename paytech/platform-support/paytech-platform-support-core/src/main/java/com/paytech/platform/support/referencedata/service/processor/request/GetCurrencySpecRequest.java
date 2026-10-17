package com.paytech.platform.support.referencedata.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按币种代码查询规则。 */
@Getter
@AllArgsConstructor
public class GetCurrencySpecRequest {

    /**
     * ISO 4217 三位币种代码
     * <li>三位大写字母。</li>
     */
    @NotNull(message = "currency 必填")
    @Pattern(regexp = "[A-Z]{3}", message = "currency 必须是三位大写 ISO 4217 代码")
    private final String currency;
}
