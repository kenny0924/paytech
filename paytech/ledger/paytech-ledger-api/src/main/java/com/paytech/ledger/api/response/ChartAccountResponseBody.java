package com.paytech.ledger.api.response;

import lombok.Getter;
import lombok.Setter;

/** 一个会计科目。 */
@Getter
@Setter
public class ChartAccountResponseBody {
    /** 科目主键。 */
    private String chartAccountId;

    /** 所属科目表主键。 */
    private String chartId;

    /**
     * 科目编号
     * <li>4-2-2-2 分级数字编号，一级 4 位、每下一级加 2 位；下级以上级编号开头。</li>
     */
    private String accountNo;

    /** 科目代码。 */
    private String accountCode;

    /** 科目名称。 */
    private String accountName;

    /** 科目类别：ASSET、LIABILITY、EQUITY、REVENUE 或 EXPENSE。 */
    private String accountClass;

    /** 正常余额方向：DEBIT 或 CREDIT。 */
    private String normalSide;

    /** 是否允许直接过账；汇总科目为 false。 */
    private Boolean postingAllowed;

    /**
     * 直接上级科目主键
     * <li>一级科目为空。</li>
     */
    private String parentChartAccountId;

    /** 级次 1—4，由科目编号长度推出。 */
    private Integer level;

    /**
     * 科目启停状态
     * <li>ACTIVE 或 INACTIVE；停用后不能新建账户、不能被新规则引用，已有账户照常记账与查询。</li>
     */
    private String accountStatus;
}
