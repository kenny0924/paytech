package com.paytech.account.domain;

import java.math.BigDecimal;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.EntrySide;
import com.paytech.account.core.enums.NormalSide;

/**
 * 一条经济事实明细映射出的一个余额变化
 *
 * @param lineNo 来源明细行号；交易事实总额为 0
 * @param accountCode ledger 标准科目代码
 * @param ownerType 核算对象类型
 * @param ownerId 核算对象标识
 * @param bucket 余额桶
 * @param normalSide 科目正常方向
 * @param entrySide 本次借贷方向
 * @param amount 正金额
 */
public record BalanceEffect(int lineNo, String accountCode, OwnerType ownerType, String ownerId, BalanceBucket bucket,
    NormalSide normalSide, EntrySide entrySide, BigDecimal amount) {

    /** 规范文本：行号|科目|Owner 类型|Owner|余额桶|方向|金额。 */
    public String canonical() {
        return lineNo + "|" + accountCode + "|" + ownerType.getCode() + "|" + ownerId + "|" + bucket.getCode() + "|"
            + entrySide.getCode() + "|" + amount.stripTrailingZeros().toPlainString();
    }
}
