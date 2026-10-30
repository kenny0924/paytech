package com.paytech.payout.api.beneficiary.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 收款账户原值（P10）
 * <li>只返回给 Operations BFF；响应 Cache-Control: no-store，不缓存、不写普通日志；本类不生成 toString。</li>
 * <li>提交时为空的字段仍为空。</li>
 */
@Getter
@Setter
public class RevealBeneficiaryAccountResponseBody {
    /** 收款账户 ID */
    private String beneficiaryAccountId;

    /** 户名原值 */
    private String holderName;

    /** IBAN 原值 */
    private String iban;

    /** 账号原值 */
    private String accountNumber;

    /** 本地银行代码 */
    private String bankCode;

    /** SWIFT BIC */
    private String swiftBic;

    /** 账户类型 */
    private String accountType;
}
