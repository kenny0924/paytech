package com.paytech.finance.accounting.core.dal.dataobject;

import com.paytech.finance.accounting.domain.enums.AccountStatus;
import com.paytech.finance.accounting.domain.enums.BalanceBucket;
import com.paytech.finance.accounting.domain.enums.NormalSide;
import java.math.BigDecimal;

/** 余额查询所需的账户、科目与账簿只读投影。 */
public record BalanceAccountRow(
    /** 统一记账账户的本库主键。 */
    long accountId,
    /** 开放的会计科目代码，用于选择已批准的余额语义。 */
    String accountCode,
    /** 账户的成熟或锁定余额桶。 */
    BalanceBucket balanceBucket,
    /** 创建账户时从科目冻结的正常余额方向。 */
    NormalSide accountNormalSide,
    /** 当前科目定义的正常余额方向，用于检查投影没有漂移。 */
    NormalSide chartNormalSide,
    /** 科目是否允许 Journal Line 直接过账。 */
    Boolean postingAllowed,
    /** 账户引用的科目所属 Chart；悬空引用时为空。 */
    Long accountChartId,
    /** 账簿当前采用的 Chart。 */
    long bookChartId,
    /** 账户当前状态；关闭只禁止新过账，不隐藏余额。 */
    AccountStatus accountStatus,
    /** 按正常方向计算、允许为负的当前净余额。 */
    BigDecimal netBalance) {
}
