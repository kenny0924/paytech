package com.paytech.account.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询一个 Owner 在经营主体币种账簿下的分类余额。 */
@Getter
@AllArgsConstructor
public class OwnerBalanceQuery {
    /** Client 或 Local Partner 的规范十进制标识。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String ownerId;

    /** Operating Entity 的 entityNo。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{2}(00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})")
    private final String entityNo;

    /** 大写三字母币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private final String currency;
}
