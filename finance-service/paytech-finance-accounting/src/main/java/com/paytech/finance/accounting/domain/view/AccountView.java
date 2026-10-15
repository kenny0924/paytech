package com.paytech.finance.accounting.domain.view;

import java.math.BigDecimal;

import com.paytech.finance.accounting.core.enums.AccountStatus;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.NormalSide;
import com.paytech.finance.accounting.core.enums.OwnerType;

import lombok.Getter;
import lombok.Setter;

/** 账户余额投影与其科目代码的组合读取结果。 */
@Getter
@Setter
public class AccountView {
    /** 账户主键。 */
    private Long accountId;

    /** 科目代码。 */
    private String accountCode;

    /** 核算对象类型。 */
    private OwnerType ownerType;

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
