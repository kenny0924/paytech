package com.paytech.account.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.foundation.money.Money;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 余额桶移动命令；Code 已由入站转换解析为枚举，组合规则由 Processor 校验。 */
@Getter
@AllArgsConstructor
public class MoveBalanceRequest {
    /** 来源类型。 */
    @NotNull
    private final MovementSourceType sourceType;

    /** 来源业务的稳定引用，1–128 个可见 ASCII 字符。 */
    @NotBlank
    @Pattern(regexp = "[\\x21-\\x7E]{1,128}")
    private final String sourceRef;

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

    /** 科目代码。 */
    @NotBlank
    private final String accountCode;

    /** 核算对象类型。 */
    @NotNull
    private final OwnerType ownerType;

    /** 核算对象规范十进制标识。 */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String ownerId;

    /** 转出余额桶。 */
    @NotNull
    private final BalanceBucket fromBucket;

    /** 转入余额桶。 */
    @NotNull
    private final BalanceBucket toBucket;

    /** 移动金额。 */
    @NotNull
    private final Money amount;

    /** 发起人；系统调用为空。 */
    private final Long operatorId;
}
