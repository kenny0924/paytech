package com.paytech.finance.accounting.domain.model;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/** Local Partner 在一个经营主体币种账簿下的分类余额。 */
@Getter
@Setter
public class PartnerBalance {
    /** Local Partner 规范十进制标识。 */
    private String partnerId;

    /** 余额所属 Operating Entity 的 entityNo。 */
    private String entityNo;

    /** 账簿币种。 */
    private String currency;

    /** PARTNER_PAYABLE/PENDING 余额。 */
    private Money pendingAmount;

    /** PARTNER_PAYABLE/AVAILABLE 余额。 */
    private Money availableAmount;

    /** PARTNER_PAYABLE/LOCKED 余额。 */
    private Money lockedAmount;

    /** PARTNER_RECOVERY_RECEIVABLE/NONE 余额。 */
    private Money recoveryReceivableAmount;
}
