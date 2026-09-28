package com.paytech.finance.api.balance;

import com.paytech.foundation.money.Money;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 余额查询模型。ID 使用字符串，每个 Money 包含币种和普通十进制金额字符串。金额单位为主货币单位，不是分；不隐式舍入。
 * 不把保证金、追偿和应付相加为总余额，也不将合法反向余额取绝对值。
 */
public final class BalanceModels {

    private BalanceModels() {}

    /** Client 当前分类余额；成功响应所有字段必填。 */
    public record ClientBalanceResponseBody(
        /** 当前已授权查询的 Client 标识。 */
        String clientId,
        /** 此余额所属的 Operating Entity，不是平台 Finance Entity。 */
        String operatingEntityId,
        /** 所有金额共同使用的大写 ISO 4217 币种。 */
        String currency,
        /** CLIENT_PAYABLE/PENDING：尚未按合同 T+N 成熟的应付余额。 */
        Money pendingAmount,
        /** CLIENT_PAYABLE/AVAILABLE：可供申请使用的余额，不等于最终可出款金额。 */
        Money availableAmount,
        /** CLIENT_PAYABLE/LOCKED：已为出款锁定且尚未释放或清算的余额。 */
        Money lockedAmount,
        /** CLIENT_SECURITY_DEPOSIT/NONE：预存保证金余额，不属于可用应付款。 */
        Money securityDepositAmount,
        /** CLIENT_RECOVERY_RECEIVABLE/NONE：待向 Client 收回的金额，正常应收为正数。 */
        Money recoveryReceivableAmount) {
        public ClientBalanceResponseBody {
            validateCurrencies(currency, pendingAmount, availableAmount, lockedAmount, securityDepositAmount, recoveryReceivableAmount);
        }
    }

    /** Local Partner 当前分类余额；成功响应所有字段必填。 */
    public record PartnerBalanceResponseBody(
        /** 当前已授权查询的 Local Partner 标识。 */
        String partnerId,
        /** 此余额所属的 Operating Entity，不跨 OE 合并。 */
        String operatingEntityId,
        /** 所有金额共同使用的大写 ISO 4217 币种。 */
        String currency,
        /** PARTNER_PAYABLE/PENDING：尚未按合同成熟的分成应付余额。 */
        Money pendingAmount,
        /** PARTNER_PAYABLE/AVAILABLE：已成熟的可用分成，不表示当前 Risk 门禁通过。 */
        Money availableAmount,
        /** PARTNER_PAYABLE/LOCKED：已经为出款锁定的分成余额。 */
        Money lockedAmount,
        /** PARTNER_RECOVERY_RECEIVABLE/NONE：待向 Partner 收回的金额，正常应收为正数。 */
        Money recoveryReceivableAmount) {
        public PartnerBalanceResponseBody {
            validateCurrencies(currency, pendingAmount, availableAmount, lockedAmount, recoveryReceivableAmount);
        }
    }
    private static void validateCurrencies(String currency, Money... balances) {
        for (Money balance : balances) {
            if (balance == null || !balance.getCurrency().equals(currency)) {
                throw new InvalidParameterException("余额必须非空且与查询币种一致");
            }
        }
    }
}
