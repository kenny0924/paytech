package com.paytech.account.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按入账流重建主体与币种下的 Owner 账户余额。 */
@Getter
@AllArgsConstructor
public class RebuildPostingStreamsRequest {
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

    /** 发起人；只用于日志审计。 */
    @Positive
    private final long operatorId;
}
