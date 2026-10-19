package com.paytech.account.domain;

import java.math.BigDecimal;

import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.core.dal.dataobject.OwnerAccount;

/**
 * 待写入批次的一行流水：已解析的 Owner 账户、方向与金额
 *
 * @param account 已存在或本事务刚开立的 Owner 账户
 * @param entrySide 借贷方向
 * @param amount 正金额
 * @param factLineNo 来源事实明细行号；桶移动为空
 * @param reversesEntryId 冲回的原流水；普通行为空
 */
public record PostingLine(OwnerAccount account, EntrySide entrySide, BigDecimal amount, Integer factLineNo,
    Long reversesEntryId) {
}
