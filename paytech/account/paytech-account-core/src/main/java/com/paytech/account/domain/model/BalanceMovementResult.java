package com.paytech.account.domain.model;

import com.paytech.account.core.dal.dataobject.AccountPosting;
import com.paytech.account.core.dal.dataobject.BalanceMovement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次桶移动命令的结果：首次执行或同来源重放。 */
@Getter
@AllArgsConstructor
public class BalanceMovementResult {
    /** 移动记录。 */
    private final BalanceMovement movement;

    /** 移动生成的入账批次。 */
    private final AccountPosting posting;

    /** 是否为同来源重放。 */
    private final boolean replayed;
}
