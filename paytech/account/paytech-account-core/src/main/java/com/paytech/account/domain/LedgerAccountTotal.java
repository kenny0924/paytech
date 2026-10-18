package com.paytech.account.domain;

import java.math.BigDecimal;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;

/**
 * ledger 按全部分录聚合的一个 Owner 账户余额（日终核对的 ledger 侧）
 *
 * @param accountId ledger 账户主键
 * @param accountCode 科目代码
 * @param ownerType 核算对象类型
 * @param ownerId 核算对象标识
 * @param bucket 余额桶
 * @param netBalance 按正常方向的净余额
 */
public record LedgerAccountTotal(long accountId, String accountCode, OwnerType ownerType, String ownerId,
    BalanceBucket bucket, BigDecimal netBalance) {
}
