package com.paytech.account.domain.model;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/** Client 在一个经营主体币种账簿下的分类余额。 */
@Getter
@Setter
public class ClientBalance {
    /** Client 规范十进制标识。 */
    private String clientId;

    /** 余额所属 Operating Entity 的 entityNo。 */
    private String entityNo;

    /** 账簿币种。 */
    private String currency;

    /** CLIENT_PAYABLE/PENDING 余额。 */
    private Money pendingAmount;

    /** CLIENT_PAYABLE/AVAILABLE 余额。 */
    private Money availableAmount;

    /** CLIENT_PAYABLE/LOCKED 余额。 */
    private Money lockedAmount;

    /** CLIENT_SECURITY_DEPOSIT/NONE 余额。 */
    private Money securityDepositAmount;

    /** CLIENT_RECOVERY_RECEIVABLE/NONE 余额。 */
    private Money recoveryReceivableAmount;
}
