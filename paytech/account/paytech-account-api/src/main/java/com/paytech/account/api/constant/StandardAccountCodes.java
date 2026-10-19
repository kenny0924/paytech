package com.paytech.account.api.constant;

/**
 * 跨服务依赖的标准科目代码；科目目录本身是开放集合，以 ledger 初始化 DML 为准
 * <li>前六个是 account 明细账的 Owner 账户类型（{@code OwnerAccountType}），在 ledger 中是控制科目。</li>
 * <li>两个渠道常量只在 ledger 使用（渠道账户不在 account 明细账）。</li>
 */
public final class StandardAccountCodes {
    public static final String CLIENT_PAYABLE = "CLIENT_PAYABLE";
    public static final String CLIENT_RECOVERY_RECEIVABLE = "CLIENT_RECOVERY_RECEIVABLE";
    public static final String CLIENT_SECURITY_DEPOSIT = "CLIENT_SECURITY_DEPOSIT";
    public static final String PARTNER_PAYABLE = "PARTNER_PAYABLE";
    public static final String PARTNER_RECOVERY_RECEIVABLE = "PARTNER_RECOVERY_RECEIVABLE";
    /** 银行现金：account 中为 CASH_ACCOUNT 的 AVAILABLE / LOCKED 出款容量，ledger 中为 CASH_ACCOUNT / NONE。 */
    public static final String BANK_CASH = "BANK_CASH";
    /** 渠道待清算应收：交易总额由渠道应付（ledger CHANNEL_ACCOUNT Owner）。 */
    public static final String CHANNEL_UNSETTLED_RECEIVABLE = "CHANNEL_UNSETTLED_RECEIVABLE";
    /** 渠道费用预提：预计渠道成本（ledger CHANNEL_ACCOUNT Owner）。 */
    public static final String CHANNEL_FEE_ACCRUAL = "CHANNEL_FEE_ACCRUAL";

    private StandardAccountCodes() {
    }
}
