package com.paytech.reconciliation.domain;

import com.paytech.reconciliation.core.dal.dataobject.CashAccount;

/** 银行账户提交复核内容的摘要：新建与关闭申请两种，复核人须原样确认（AC-03）。 */
public final class CashAccountReviews {
    private CashAccountReviews() {
    }

    /** 新建：账户主键、主体、用途、银行、名称、币种、令牌、末四位与提交人。 */
    public static String createHash(CashAccount account) {
        return ReconResolutionRules.sha256(String.join("\n", "CREATE", account.getId().toString(),
            account.getEntityType().getCode(), account.getEntityId(), account.getAccountPurpose().getCode(),
            account.getBankCode(), account.getAccountName(), account.getCurrency(), account.getAccountVaultToken(),
            account.getAccountLast4(), account.getMakerId().toString()));
    }

    /** 关闭申请：账户主键、申请时版本与申请人。 */
    public static String closeHash(CashAccount account, Long makerId) {
        return ReconResolutionRules.sha256(String.join("\n", "CLOSE", account.getId().toString(),
            account.getVersion().toString(), makerId.toString()));
    }
}
