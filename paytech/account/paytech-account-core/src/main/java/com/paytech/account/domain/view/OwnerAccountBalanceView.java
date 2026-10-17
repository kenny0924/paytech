package com.paytech.account.domain.view;

import java.math.BigDecimal;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.core.enums.NormalSide;

import lombok.Getter;
import lombok.Setter;

/** Owner 分类余额查询所需的账户与科目组合读取结果。 */
@Getter
@Setter
public class OwnerAccountBalanceView {
    /** 统一记账账户主键。 */
    private Long accountId;

    /**
     * 账户引用科目的代码
     * <li>科目悬空时为空，由查询方 Fail-Closed。</li>
     */
    private String accountCode;

    /** 账户的成熟或锁定余额桶。 */
    private BalanceBucket balanceBucket;

    /** 创建账户时从科目冻结的正常余额方向。 */
    private NormalSide accountNormalSide;

    /** 科目当前定义的正常余额方向，用于发现投影漂移。 */
    private NormalSide chartNormalSide;

    /** 科目是否允许直接过账。 */
    private Boolean postingAllowed;

    /** 账户引用科目所属的科目表主键。 */
    private Long accountChartId;

    /** 按正常方向计算、允许为负的当前净余额。 */
    private BigDecimal netBalance;
}
