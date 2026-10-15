package com.paytech.finance.accounting.core.constant;

/** 记账内核使用的开放目录代码与固定前缀。 */
public final class AccountingCodes {
    /** 人工会计调整生成的记账指令类型。 */
    public static final String MANUAL_ADJUSTMENT_INSTRUCTION = "MANUAL_ADJUSTMENT";

    /** 人工调整业务单号前缀，后接调整主键。 */
    public static final String MANUAL_ADJUSTMENT_NO_PREFIX = "MA";

    /** 账簿序号行的首个可分配值。 */
    public static final long FIRST_JOURNAL_SEQUENCE = 1L;

    private AccountingCodes() {
    }
}
