package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 申报角色（t_tax_reporting_entry.entry_role）；本期只有 OUTPUT。 */
public enum ReportingEntryRole implements CodeEnum {
    /** 销项 */
    OUTPUT,

    /** 进项 */
    INPUT,

    /** 反向征税销项 */
    RCM_OUTPUT,

    /** 反向征税进项 */
    RCM_INPUT,

    /** 只披露 */
    DISCLOSURE;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
