package com.paytech.finance.accounting.core.constant;

/** 记账内核使用的开放目录代码与固定前缀。 */
public final class AccountingCodes {
    /** 人工会计调整生成的记账指令类型。 */
    public static final String MANUAL_ADJUSTMENT_INSTRUCTION = "MANUAL_ADJUSTMENT";

    /** 人工调整业务单号前缀，后接调整主键。 */
    public static final String MANUAL_ADJUSTMENT_NO_PREFIX = "MA";

    /** 期间关账损益结转生成的记账指令类型。 */
    public static final String PERIOD_PROFIT_CLOSE_INSTRUCTION = "PERIOD_PROFIT_CLOSE";

    /** Finance 自身作为自动指令来源系统时使用的系统代码。 */
    public static final String FINANCE_SOURCE_SYSTEM = "FINANCE";

    /** 自动记账规则行科目校验使用的标准科目表代码。 */
    public static final String STANDARD_CHART_CODE = "STANDARD";

    /** 账簿序号行的首个可分配值。 */
    public static final long FIRST_JOURNAL_SEQUENCE = 1L;

    private AccountingCodes() {
    }
}
