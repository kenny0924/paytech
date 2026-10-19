package com.paytech.operations.console.bff.service.model;

import com.paytech.foundation.money.Money;

import lombok.Getter;
import lombok.Setter;

/**
 * Owner 余额查询页面模型
 * <li>字段与 account 合同一一对应，由 Client 适配器严格转换；金额为 Foundation Money（币种 + 主单位字符串）。</li>
 * <li>Available 不等于可出款资格；本页只展示 account 的余额投影。</li>
 *
 * @author Claude
 */
public final class FinanceBalanceModels {

    /** 只承载类型，禁止创建实例。 */
    private FinanceBalanceModels() {
    }

    /** Client 在一个 Operating Entity 与币种下的分类余额。 */
    @Getter
    @Setter
    public static class ClientBalanceResponseBody {
        /** Client ID，与请求一致。 */
        private String clientId;

        /** Operating Entity 的 entityNo，与请求一致。 */
        private String entityNo;

        /** 币种，与请求一致。 */
        private String currency;

        /** CLIENT_PAYABLE/PENDING：尚未成熟的应付余额。 */
        private Money pendingAmount;

        /** CLIENT_PAYABLE/AVAILABLE：可供申请使用的余额，不等于最终可出款金额。 */
        private Money availableAmount;

        /** CLIENT_PAYABLE/LOCKED：已为出款锁定的余额。 */
        private Money lockedAmount;

        /** CLIENT_SECURITY_DEPOSIT/NONE：预存保证金余额。 */
        private Money securityDepositAmount;

        /** CLIENT_RECOVERY_RECEIVABLE/NONE：待向 Client 收回的金额。 */
        private Money recoveryReceivableAmount;
    }

    /** Local Partner 在一个 Operating Entity 与币种下的分类余额；不含保证金。 */
    @Getter
    @Setter
    public static class PartnerBalanceResponseBody {
        /** Local Partner ID，与请求一致。 */
        private String partnerId;

        /** Operating Entity 的 entityNo，与请求一致。 */
        private String entityNo;

        /** 币种，与请求一致。 */
        private String currency;

        /** PARTNER_PAYABLE/PENDING：尚未成熟的分成应付余额。 */
        private Money pendingAmount;

        /** PARTNER_PAYABLE/AVAILABLE：已成熟的可用分成。 */
        private Money availableAmount;

        /** PARTNER_PAYABLE/LOCKED：已为出款锁定的分成余额。 */
        private Money lockedAmount;

        /** PARTNER_RECOVERY_RECEIVABLE/NONE：待向 Partner 收回的金额。 */
        private Money recoveryReceivableAmount;
    }
}
