package com.paytech.finance.accounting.core.dal.dataobject;

/** Operating Entity 与币种唯一定位出的账簿读取投影。 */
public record LedgerBookRow(
    /** 总账账簿本库主键。 */
    long id,
    /** 账簿采用的会计科目表主键。 */
    long chartId) {
}
