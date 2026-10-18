package com.paytech.account.api.constant;

/** 余额查询、账户身份矩阵与余额映射规则依赖的标准科目代码；科目目录本身是开放集合，以 ledger 初始化 DML 为准。 */
public final class StandardAccountCodes {
    public static final String CLIENT_PAYABLE = "CLIENT_PAYABLE";
    public static final String CLIENT_RECOVERY_RECEIVABLE = "CLIENT_RECOVERY_RECEIVABLE";
    public static final String CLIENT_SECURITY_DEPOSIT = "CLIENT_SECURITY_DEPOSIT";
    public static final String PARTNER_PAYABLE = "PARTNER_PAYABLE";
    public static final String PARTNER_RECOVERY_RECEIVABLE = "PARTNER_RECOVERY_RECEIVABLE";
    /** 渠道待清算应收：交易总额由渠道应付（CHANNEL_ACCOUNT Owner）。 */
    public static final String CHANNEL_UNSETTLED_RECEIVABLE = "CHANNEL_UNSETTLED_RECEIVABLE";
    /** 渠道费用预提：预计渠道成本（CHANNEL_ACCOUNT Owner）。 */
    public static final String CHANNEL_FEE_ACCRUAL = "CHANNEL_FEE_ACCRUAL";

    private StandardAccountCodes() {
    }
}
