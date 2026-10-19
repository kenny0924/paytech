package com.paytech.account.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按主体与币种的读取请求（事实应用进度）。 */
@Getter
@AllArgsConstructor
public class EntityCurrencyQuery {
    /** 主体类型。 */
    @NotNull
    private final AccountEntityType entityType;

    /** 主体标识。 */
    @NotBlank
    private final String entityId;

    /** 大写三字母币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private final String currency;
}
