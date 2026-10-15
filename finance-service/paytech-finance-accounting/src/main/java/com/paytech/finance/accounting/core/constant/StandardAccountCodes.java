package com.paytech.finance.accounting.core.constant;

/** 余额查询、账户身份矩阵等核心业务依赖的标准科目代码；科目目录本身是开放集合，以初始化 DML 为准。 */
public final class StandardAccountCodes {
    public static final String CLIENT_PAYABLE = "CLIENT_PAYABLE";
    public static final String CLIENT_RECOVERY_RECEIVABLE = "CLIENT_RECOVERY_RECEIVABLE";
    public static final String CLIENT_SECURITY_DEPOSIT = "CLIENT_SECURITY_DEPOSIT";
    public static final String PARTNER_PAYABLE = "PARTNER_PAYABLE";
    public static final String PARTNER_RECOVERY_RECEIVABLE = "PARTNER_RECOVERY_RECEIVABLE";

    private StandardAccountCodes() {
    }
}
