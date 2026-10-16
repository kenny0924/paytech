package com.paytech.finance.accounting.core.dal.dataobject;

import java.time.Instant;

import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.AutoInstructionType;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.EntrySide;
import com.paytech.finance.accounting.core.enums.OwnerRole;

import lombok.Getter;
import lombok.Setter;

/** t_accounting_rule_line：把一个已计算金额来源确定性映射为借贷方向、科目、核算对象角色和余额桶，不可变。 */
@Getter
@Setter
public class AccountingRuleLine {
    /**
     * 自动记账规则行的 Snowflake 主键
     */
    private Long id;

    /**
     * 所属自动记账规则版本主键
     */
    private Long bundleVerId;

    /**
     * 该规则行适用的指令类型
     */
    private AutoInstructionType instructionType;

    /**
     * 同一版本与指令类型内稳定有序的行号
     */
    private Integer lineNo;

    /**
     * 同一版本与指令类型内稳定识别会计含义的行代码
     */
    private String lineCode;

    /**
     * 金额来源
     */
    private AmountSource amountSourceCode;

    /**
     * 核算对象角色
     */
    private OwnerRole ownerRole;

    /**
     * 在账簿科目表中唯一解析的叶子科目代码
     */
    private String accountCode;

    /**
     * 统一账户余额桶
     */
    private BalanceBucket balanceBucket;

    /**
     * 借贷方向
     */
    private EntrySide entrySide;

    /**
     * 金额来源缺失时是否拒绝生成指令
     */
    private Boolean requiredFlag;

    /**
     * 该记录在 Finance 数据库中的 UTC 微秒创建时间
     */
    private Instant createTime;
}
