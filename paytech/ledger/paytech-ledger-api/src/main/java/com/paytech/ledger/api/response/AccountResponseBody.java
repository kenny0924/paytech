package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账户列表项：身份与当前余额投影。 */
@Getter
@Setter
public class AccountResponseBody {
    /** 账户主键。 */
    private String accountId;

    /** 科目代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private String ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /**
     * 余额桶
     * <li>PENDING、AVAILABLE、LOCKED 或 NONE。</li>
     */
    private String balanceBucket;

    /**
     * 正常余额方向
     * <li>DEBIT 或 CREDIT。</li>
     */
    private String normalSide;

    /**
     * 账户状态
     * <li>ACTIVE、INACTIVE 或 CLOSED。</li>
     */
    private String accountStatus;

    /** 累计借方，主货币单位，按账簿币种精度补齐。 */
    private String debitTotal;

    /** 累计贷方，主货币单位，按账簿币种精度补齐。 */
    private String creditTotal;

    /**
     * 净余额，主货币单位
     * <li>按正常方向计算，负数表示反向余额。</li>
     */
    private String netBalance;

    /** 已应用到余额的最后分录序号；没有分录时为 0。 */
    private String lastJournalSequence;
}
