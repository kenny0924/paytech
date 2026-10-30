package com.paytech.payout.api.balance.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Owner 余额解释
 * <li>金额为币种主单位十进制字符串，按币种精度补齐；币种在所属位置对象上。</li>
 */
@Getter
@Setter
public class BalanceExplanationResponseBody {
    /** Owner 类型 */
    private String ownerType;

    /** Owner 标识 */
    private String ownerId;

    /** 每个 OE × 币种一条，按 entityNo、币种升序 */
    private List<BalancePosition> positions;

    /** 每个有收款账户的币种一条，按币种升序 */
    private List<UsableAccountSummary> usableAccounts;

    /** 一个 OE × 币种的余额与阻断原因。 */
    @Getter
    @Setter
    public static class BalancePosition {
        /** Operating Entity 编号 */
        private String entityNo;

        /** 币种 */
        private String currency;

        /** 待结算（PENDING） */
        private String pending;

        /** 可用（AVAILABLE） */
        private String available;

        /** 锁定（LOCKED） */
        private String locked;

        /**
         * 保证金
         * <li>只有 Client 有；Local Partner 为空。</li>
         */
        private String securityDeposit;

        /** 待收回的追偿应收 */
        private String recoveryReceivable;

        /** 当前不能提款的原因（BeneficiaryBlockReason）；可提款时为空数组 */
        private List<String> blockReasons;
    }

    /** 一个币种的收款账户可用情况。 */
    @Getter
    @Setter
    public static class UsableAccountSummary {
        /** 币种 */
        private String currency;

        /** 可用（VERIFIED 且冷却结束）的账户数 */
        private int count;

        /** 默认账户 ID；没有默认时为空 */
        private String defaultAccountId;
    }
}
