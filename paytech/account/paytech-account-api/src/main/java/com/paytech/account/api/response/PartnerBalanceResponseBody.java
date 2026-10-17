package com.paytech.account.api.response;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/**
 * Local Partner 当前分类余额
 * <li>每个 Money 含币种与主货币单位十进制金额，与顶层币种一致；不跨 OE 合并。</li>
 */
@Getter
@Setter
public class PartnerBalanceResponseBody {
    /** 查询的 Local Partner 标识。 */
    private String partnerId;

    /** 余额所属 Operating Entity 的 entityNo。 */
    private String entityNo;

    /** 所有金额共同使用的大写 ISO 4217 币种。 */
    private String currency;

    /** PARTNER_PAYABLE/PENDING：尚未按合同成熟的分成应付余额。 */
    private Money pendingAmount;

    /** PARTNER_PAYABLE/AVAILABLE：已成熟的可用分成，不表示当前 Risk 门禁通过。 */
    private Money availableAmount;

    /** PARTNER_PAYABLE/LOCKED：已经为出款锁定的分成余额。 */
    private Money lockedAmount;

    /** PARTNER_RECOVERY_RECEIVABLE/NONE：待向 Partner 收回的金额，正常应收为正数。 */
    private Money recoveryReceivableAmount;
}
