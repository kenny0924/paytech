package com.paytech.account.api.enums;

import java.util.EnumSet;
import java.util.Set;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.foundation.common.enums.CodeEnum;

/**
 * account 明细账的 Owner 账户类型目录（不建表）：科目代码、所属 Owner 类型、正常方向与允许的余额桶
 * <li>Code 即 ledger 科目代码；ledger 以同一代码作为控制科目，测试断言每个代码在科目表中存在且可过账。</li>
 * <li>开户与入账按本目录校验 (accountCode, ownerType, balanceBucket) 组合；CLIENT_SECURITY_DEPOSIT 与两个 RECOVERY_RECEIVABLE 本期无事实来源，只存在于目录与开户校验。</li>
 * <li>科目编号是 ledger STANDARD 科目表中同代码科目的分级编号，只用于生成可读账户号；ledger 测试核对两边一致，account 运行时不读 ledger
 * （2026-10-19 科目分级与可读账户号决定第 8 条）。</li>
 */
public enum OwnerAccountType implements CodeEnum {
    /** Client 应付：交易本金与费用的应付余额，分 PENDING / AVAILABLE / LOCKED */
    CLIENT_PAYABLE(StandardAccountCodes.CLIENT_PAYABLE, "22010101", OwnerType.CLIENT, EntrySide.CREDIT,
        EnumSet.of(BalanceBucket.PENDING, BalanceBucket.AVAILABLE, BalanceBucket.LOCKED)),

    /** Client 预存保证金：负债，不分桶 */
    CLIENT_SECURITY_DEPOSIT(StandardAccountCodes.CLIENT_SECURITY_DEPOSIT, "22110101", OwnerType.CLIENT,
        EntrySide.CREDIT, EnumSet.of(BalanceBucket.NONE)),

    /** Client 追偿应收：资产，不分桶 */
    CLIENT_RECOVERY_RECEIVABLE(StandardAccountCodes.CLIENT_RECOVERY_RECEIVABLE, "11310101", OwnerType.CLIENT,
        EntrySide.DEBIT, EnumSet.of(BalanceBucket.NONE)),

    /** Local Partner 分成应付，分 PENDING / AVAILABLE / LOCKED */
    PARTNER_PAYABLE(StandardAccountCodes.PARTNER_PAYABLE, "22010201", OwnerType.LOCAL_PARTNER, EntrySide.CREDIT,
        EnumSet.of(BalanceBucket.PENDING, BalanceBucket.AVAILABLE, BalanceBucket.LOCKED)),

    /** Local Partner 追偿应收：资产，不分桶 */
    PARTNER_RECOVERY_RECEIVABLE(StandardAccountCodes.PARTNER_RECOVERY_RECEIVABLE, "11310102",
        OwnerType.LOCAL_PARTNER, EntrySide.DEBIT, EnumSet.of(BalanceBucket.NONE)),

    /** 银行现金出款容量：AVAILABLE / LOCKED；ledger 中同代码仍为 CASH_ACCOUNT / NONE */
    BANK_CASH(StandardAccountCodes.BANK_CASH, "10010101", OwnerType.CASH_ACCOUNT, EntrySide.DEBIT,
        EnumSet.of(BalanceBucket.AVAILABLE, BalanceBucket.LOCKED));

    private final String code;

    private final String chartAccountNo;

    private final OwnerType ownerType;

    private final EntrySide normalSide;

    private final Set<BalanceBucket> allowedBuckets;

    /** 固定科目代码、科目编号、Owner 类型、正常方向与允许桶。 */
    OwnerAccountType(String code, String chartAccountNo, OwnerType ownerType, EntrySide normalSide,
        EnumSet<BalanceBucket> allowedBuckets) {
        this.code = code;
        this.chartAccountNo = chartAccountNo;
        this.ownerType = ownerType;
        this.normalSide = normalSide;
        this.allowedBuckets = Set.copyOf(allowedBuckets);
    }

    /** 科目代码，与 {@link StandardAccountCodes} 及 ledger 科目表一致。 */
    @Override
    public String getCode() {
        return code;
    }

    /** ledger STANDARD 科目表中同代码科目的分级编号，可读账户号的科目段。 */
    public String getChartAccountNo() {
        return chartAccountNo;
    }

    /** 该账户类型唯一允许的 Owner 类型。 */
    public OwnerType getOwnerType() {
        return ownerType;
    }

    /** 正常余额方向：净余额按此方向计算。 */
    public EntrySide getNormalSide() {
        return normalSide;
    }

    /** 允许的余额桶（不可修改）。 */
    public Set<BalanceBucket> getAllowedBuckets() {
        return allowedBuckets;
    }

    /**
     * 判断 Owner 类型与余额桶组合是否属于本账户类型
     *
     * @param owner Owner 类型
     * @param bucket 余额桶
     * @return Owner 类型一致且桶在允许集合内时为真
     */
    public boolean allows(OwnerType owner, BalanceBucket bucket) {
        return ownerType == owner && allowedBuckets.contains(bucket);
    }

    /**
     * 按科目代码查找账户类型
     *
     * @param accountCode 科目代码
     * @return 账户类型；不属于 account 明细账目录时为空
     */
    public static OwnerAccountType ofCode(String accountCode) {
        for (OwnerAccountType type : values()) {
            if (type.code.equals(accountCode)) {
                return type;
            }
        }
        return null;
    }
}
