package com.paytech.account.api.response;

import lombok.Getter;
import lombok.Setter;

/** 账簿内一个余额账户；金额为主货币单位十进制字符串，按币种精度补齐。 */
@Getter
@Setter
public class BookBalanceResponseBody {
    /** 账户服务的余额账户主键（不是 ledger 账户主键）。 */
    private String balanceId;

    /** ledger 标准科目代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private String ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /** 余额桶：PENDING、AVAILABLE、LOCKED 或 NONE。 */
    private String balanceBucket;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /** 已应用经济事实累计的借方。 */
    private String debitTotal;

    /** 已应用经济事实累计的贷方。 */
    private String creditTotal;

    /** 按正常方向计算、允许为负的净余额。 */
    private String netBalance;

    /** 最近改变本余额的经济事实；尚无变化时为空。 */
    private String lastFactId;

    /** 最近改变本余额的经济事实版本；尚无变化时为空。 */
    private String lastFactVersion;
}
