package com.paytech.account.api.response;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/** 一个 Owner 账户及其当前余额；金额币种与查询币种一致。 */
@Getter
@Setter
public class OwnerAccountResponseBody {
    /** Owner 账户主键（account t_owner_account.id，不是 ledger 账户主键）。 */
    private String accountId;

    /** 科目代码，取值见 {@code OwnerAccountType}。 */
    private String accountCode;

    /** 核算对象类型：CLIENT、LOCAL_PARTNER 或 CASH_ACCOUNT。 */
    private String ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /** 余额桶：PENDING、AVAILABLE、LOCKED 或 NONE。 */
    private String balanceBucket;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /** 账户状态：ACTIVE、FROZEN 或 CLOSED。 */
    private String accountStatus;

    /** 全部流水借方合计。 */
    private Money debitTotal;

    /** 全部流水贷方合计。 */
    private Money creditTotal;

    /**
     * 净余额
     * <li>按正常方向计算，允许为负（例如替代冲回已成熟的 PENDING）。</li>
     */
    private Money netBalance;

    /** 余额乐观锁版本，十进制字符串；每次入账、移动或重建加一。 */
    private String balanceVersion;
}
