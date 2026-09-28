package com.paytech.finance.accounting.domain.model;

import com.paytech.finance.accounting.domain.enums.BalanceBucket;

/** 由会计科目和余额桶共同确定的分类余额身份。 */
public record AccountBalanceKey(
    /** 当前余额采用的稳定会计科目代码。 */
    String accountCode,
    /** 当前余额所处的成熟或锁定桶。 */
    BalanceBucket balanceBucket) {
}
