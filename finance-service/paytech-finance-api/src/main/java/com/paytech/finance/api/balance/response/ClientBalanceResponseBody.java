package com.paytech.finance.api.balance.response;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/**
 * Client 当前分类余额
 * <li>每个 Money 含币种与主货币单位十进制金额，与顶层币种一致；不把保证金、追偿与应付相加为总余额。</li>
 */
@Getter
@Setter
public class ClientBalanceResponseBody {
    /** 查询的 Client 标识。 */
    private String clientId;

    /** 余额所属 Operating Entity 的 entityNo，不是平台 Finance Entity。 */
    private String entityNo;

    /** 所有金额共同使用的大写 ISO 4217 币种。 */
    private String currency;

    /** CLIENT_PAYABLE/PENDING：尚未按合同 T+N 成熟的应付余额。 */
    private Money pendingAmount;

    /** CLIENT_PAYABLE/AVAILABLE：可供申请使用的余额，不等于最终可出款金额。 */
    private Money availableAmount;

    /** CLIENT_PAYABLE/LOCKED：已为出款锁定且尚未释放或清算的余额。 */
    private Money lockedAmount;

    /** CLIENT_SECURITY_DEPOSIT/NONE：预存保证金余额，不属于可用应付款。 */
    private Money securityDepositAmount;

    /** CLIENT_RECOVERY_RECEIVABLE/NONE：待向 Client 收回的金额，正常应收为正数。 */
    private Money recoveryReceivableAmount;
}
