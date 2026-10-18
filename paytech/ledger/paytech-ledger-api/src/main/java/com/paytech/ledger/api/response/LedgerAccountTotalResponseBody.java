package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一个 Owner 账户按全部分录聚合的余额。 */
@Getter
@Setter
public class LedgerAccountTotalResponseBody {
    /** ledger 账户主键（全局账户 ID）。 */
    private String accountId;

    /** 科目代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private String ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /** 余额桶：PENDING、AVAILABLE、LOCKED 或 NONE。 */
    private String balanceBucket;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /** 全部分录借方合计，主货币单位，按账簿币种精度补齐。 */
    private String debitTotal;

    /** 全部分录贷方合计，主货币单位，按账簿币种精度补齐。 */
    private String creditTotal;

    /** 按正常方向计算的净余额；负数表示反向余额。 */
    private String netBalance;

    /** 涉及该账户的最大分录序号；没有分录时为 0。 */
    private String lastJournalSequence;
}
