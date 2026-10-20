package com.paytech.ledger.domain.view;

import java.math.BigDecimal;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** 账户余额投影与其科目代码的组合读取结果。 */
@Getter
@Setter
public class AccountView {
    /** 账户主键。 */
    private Long accountId;

    /** 财务人员可读账户号。 */
    private String accountNo;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 核算对象类型。 */
    private LedgerOwnerType ownerType;

    /** 核算对象标识。 */
    private String ownerId;

    /** 余额桶。 */
    private BalanceBucket balanceBucket;

    /** 正常余额方向。 */
    private NormalSide normalSide;

    /** 账户状态。 */
    private AccountStatus accountStatus;

    /** 累计借方，按币种精度规范。 */
    private BigDecimal debitTotal;

    /** 累计贷方，按币种精度规范。 */
    private BigDecimal creditTotal;

    /** 有符号净余额，按币种精度规范。 */
    private BigDecimal netBalance;

    /** 已应用的最后分录序号。 */
    private Long lastJournalSequence;
}
