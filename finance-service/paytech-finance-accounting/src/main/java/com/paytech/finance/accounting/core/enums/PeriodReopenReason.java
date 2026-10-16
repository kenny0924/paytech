package com.paytech.finance.accounting.core.enums;

import java.util.Arrays;

import com.paytech.foundation.common.enums.CodeEnum;

/** 期间重开原因目录；数据库列以字符串保存，发起时由服务端校验。 */
public enum PeriodReopenReason implements CodeEnum {
    /** 会计更正 */
    ACCOUNTING_CORRECTION,

    /** 晚到交易补记 */
    LATE_TRANSACTION,

    /** 审计调整 */
    AUDIT_ADJUSTMENT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }

    /** 判断编码是否属于本目录。 */
    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(reason -> reason.getCode().equals(code));
    }
}
