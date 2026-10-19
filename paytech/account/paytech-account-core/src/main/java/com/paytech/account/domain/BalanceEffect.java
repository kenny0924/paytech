package com.paytech.account.domain;

import java.math.BigDecimal;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerAccountType;

/**
 * 一条经济事实明细映射出的一行 Owner 账户流水
 *
 * @param factLineNo 来源明细行号
 * @param accountType Owner 账户类型（科目代码、Owner 类型与正常方向）
 * @param ownerId 核算对象标识
 * @param bucket 余额桶
 * @param entrySide 本次借贷方向
 * @param amount 正金额
 */
public record BalanceEffect(int factLineNo, OwnerAccountType accountType, String ownerId, BalanceBucket bucket,
    EntrySide entrySide, BigDecimal amount) {
}
