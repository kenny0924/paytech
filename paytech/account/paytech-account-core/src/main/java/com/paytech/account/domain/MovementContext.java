package com.paytech.account.domain;

import java.math.BigDecimal;

import com.paytech.account.api.enums.AccountEntityType;
import com.paytech.account.api.enums.MovementSourceType;
import com.paytech.account.api.enums.OwnerAccountType;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次已通过输入校验的余额桶移动命令
 * <li>来源类型、账户类型与桶方向的组合已校验；转出 / 转入桶由来源类型固定，金额为正且不超过币种精度。</li>
 */
@Getter
@AllArgsConstructor
public class MovementContext {
    /** 来源类型。 */
    private final MovementSourceType sourceType;

    /** 来源业务的稳定引用。 */
    private final String sourceRef;

    /** 账户所属主体类型。 */
    private final AccountEntityType entityType;

    /** 账户所属主体标识。 */
    private final String entityId;

    /** 币种。 */
    private final String currency;

    /** Owner 账户类型。 */
    private final OwnerAccountType accountType;

    /** 核算对象标识。 */
    private final String ownerId;

    /** 正移动金额。 */
    private final BigDecimal amount;

    /** 发起人；内部任务为空。 */
    private final Long operatorId;
}
