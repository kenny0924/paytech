package com.paytech.payout.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryBlockReason;
import com.paytech.payout.core.dal.dataobject.BeneficiaryAccount;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Owner 余额解释：各 OE × 币种的余额与阻断原因，以及各币种可用账户。 */
@Getter
@AllArgsConstructor
public class BalanceExplanation {
    /** 余额位置，按 entityNo、币种升序 */
    private final List<PositionExplanation> positions;

    /** 各币种可用账户，按币种升序 */
    private final List<UsableAccounts> usableAccounts;

    /**
     * 判定各余额位置的阻断原因与各币种可用账户（B9）
     * <li>NO_AVAILABLE_BALANCE：AVAILABLE ≤ 0；RECOVERY_OUTSTANDING：追偿应收 > 0。</li>
     * <li>该币种没有 VERIFIED 账户为 NO_USABLE_ACCOUNT；有 VERIFIED 但都在冷却期为 ACCOUNT_COOLING（按 now 实时判定）。</li>
     *
     * @param positions account 余额位置
     * @param accounts Owner 的全部收款账户
     * @param now 判定时刻
     * @return 余额解释
     */
    public static BalanceExplanation of(List<OwnerBalancePosition> positions, List<BeneficiaryAccount> accounts,
        Instant now) {
        List<PositionExplanation> explained = positions.stream()
            .sorted(Comparator.comparing(OwnerBalancePosition::getEntityNo).thenComparing(OwnerBalancePosition::getCurrency))
            .map(position -> new PositionExplanation(position, blockReasons(position, accounts, now))).toList();
        return new BalanceExplanation(explained, usableAccounts(accounts, now));
    }

    /** 一个余额位置的阻断原因，按目录顺序。 */
    static List<BeneficiaryBlockReason> blockReasons(OwnerBalancePosition position, List<BeneficiaryAccount> accounts,
        Instant now) {
        List<BeneficiaryBlockReason> reasons = new ArrayList<>();
        if (position.getAvailable().signum() <= 0) {
            reasons.add(BeneficiaryBlockReason.NO_AVAILABLE_BALANCE);
        }
        List<BeneficiaryAccount> verified = accounts.stream().filter(account -> account.getCurrency()
            .equals(position.getCurrency()) && account.getStatus() == BeneficiaryAccountStatus.VERIFIED).toList();
        if (verified.isEmpty()) {
            reasons.add(BeneficiaryBlockReason.NO_USABLE_ACCOUNT);
        } else if (verified.stream().noneMatch(account -> account.usableAt(now))) {
            reasons.add(BeneficiaryBlockReason.ACCOUNT_COOLING);
        }
        BigDecimal recovery = position.getRecoveryReceivable();
        if (recovery != null && recovery.signum() > 0) {
            reasons.add(BeneficiaryBlockReason.RECOVERY_OUTSTANDING);
        }
        return reasons;
    }

    /** 未终结账户涉及的每个币种：可用账户数与默认账户。 */
    static List<UsableAccounts> usableAccounts(List<BeneficiaryAccount> accounts, Instant now) {
        Map<String, List<BeneficiaryAccount>> byCurrency = new TreeMap<>();
        accounts.stream().filter(account -> !account.statusIn(BeneficiaryAccountStatus.REJECTED,
            BeneficiaryAccountStatus.CLOSED)).forEach(account -> byCurrency.computeIfAbsent(account.getCurrency(),
                currency -> new ArrayList<>()).add(account));
        return byCurrency.entrySet().stream().map(entry -> new UsableAccounts(entry.getKey(),
            (int)entry.getValue().stream().filter(account -> account.usableAt(now)).count(),
            entry.getValue().stream().filter(account -> Boolean.TRUE.equals(account.getIsDefault())
                && account.getStatus() == BeneficiaryAccountStatus.VERIFIED).map(BeneficiaryAccount::getId).findFirst()
                .orElse(null))).toList();
    }

    /** 一个余额位置及其阻断原因。 */
    @Getter
    @AllArgsConstructor
    public static class PositionExplanation {
        /** account 余额 */
        private final OwnerBalancePosition position;

        /** 阻断原因，可提款时为空 */
        private final List<BeneficiaryBlockReason> blockReasons;
    }

    /** 一个币种的可用账户统计。 */
    @Getter
    @AllArgsConstructor
    public static class UsableAccounts {
        /** 币种 */
        private final String currency;

        /** 可用账户数 */
        private final int count;

        /** 默认账户 ID，可为空 */
        private final Long defaultAccountId;
    }
}
