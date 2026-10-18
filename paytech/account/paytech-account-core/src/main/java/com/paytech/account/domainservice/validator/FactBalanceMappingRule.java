package com.paytech.account.domainservice.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.account.api.constant.StandardAccountCodes;
import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.account.core.enums.EntrySide;
import com.paytech.account.core.enums.NormalSide;
import com.paytech.account.domain.BalanceEffect;
import com.paytech.account.domain.FactRecord;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 经济事实到 Owner 余额变化的映射规则（design/04 §5：费用类型 × 付方 / 收方角色 → owner_type / owner_id / balance_bucket）
 * <li>只有 CLIENT、LOCAL_PARTNER、CHANNEL 三个付方 / 收方角色对应 account 维护的余额；经营主体、Finance Entity、税务机关、消费者与平台角色不产生余额变化。</li>
 * <li>收方记贷方、付方记借方；方向为 REVERSAL 时借贷互换。余额都在事实 OE 的经营主体账簿内（与 ledger 交易规则的 Owner 账户一致）。</li>
 * <li>交易事实（PAYMENT）的明细行没有付方 / 收方时按费用类型的隐含角色映射，与 ledger STANDARD_TRANSACTION 规则的 Owner 行一致：事实总额为渠道应收
 * （CHANNEL 付方），CLIENT_PRINCIPAL 为 Client 应付，PARTNER_SHARE 为 Partner 应付，CHANNEL_COST 为渠道费用预提；其余费用类型不涉及 Owner 余额。</li>
 * <li>Owner 角色出现在没有映射的费用类型上时拒绝整条事实（Fail-Closed），不静默丢弃 Owner 影响。</li>
 * <li>纯内存规则，不访问数据库；金额与舍入由 billing 一次决定（D16），这里只拒绝负数。</li>
 */
public final class FactBalanceMappingRule {
    /** 交易事实类型代码（billing EconomicsType.PAYMENT）。 */
    static final String PAYMENT = "PAYMENT";

    /** 事实总额的费用类型代码（billing AmountSource.GROSS_AMOUNT）。 */
    static final String GROSS_AMOUNT = "GROSS_AMOUNT";

    /** 冲回方向代码（billing FactLineDirection.REVERSAL）。 */
    static final String REVERSAL = "REVERSAL";

    /** 付方 / 收方中对应余额的角色代码（billing FactPartyRole）。 */
    static final String CLIENT = "CLIENT";

    static final String LOCAL_PARTNER = "LOCAL_PARTNER";

    static final String CHANNEL = "CHANNEL";

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** 角色 → 费用类型 → 余额账户。 */
    private static final Map<String, Map<String, Target>> TARGETS = Map.of(
        CLIENT, Map.of(
            "CLIENT_PRINCIPAL", new Target(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, BalanceBucket.PENDING,
                NormalSide.CREDIT),
            "CLIENT_FEE", new Target(StandardAccountCodes.CLIENT_PAYABLE, OwnerType.CLIENT, BalanceBucket.PENDING,
                NormalSide.CREDIT)),
        LOCAL_PARTNER, Map.of(
            "PARTNER_SHARE", new Target(StandardAccountCodes.PARTNER_PAYABLE, OwnerType.LOCAL_PARTNER,
                BalanceBucket.PENDING, NormalSide.CREDIT)),
        CHANNEL, Map.of(
            GROSS_AMOUNT, new Target(StandardAccountCodes.CHANNEL_UNSETTLED_RECEIVABLE, OwnerType.CHANNEL_ACCOUNT,
                BalanceBucket.NONE, NormalSide.DEBIT),
            "CHANNEL_COST", new Target(StandardAccountCodes.CHANNEL_FEE_ACCRUAL, OwnerType.CHANNEL_ACCOUNT,
                BalanceBucket.NONE, NormalSide.CREDIT)));

    /** 交易事实明细行没有付方 / 收方时的隐含收方角色。 */
    private static final Map<String, String> IMPLICIT_PAYEE = Map.of("CLIENT_PRINCIPAL", CLIENT, "PARTNER_SHARE",
        LOCAL_PARTNER, "CHANNEL_COST", CHANNEL);

    /** 不产生余额变化的角色。 */
    private static final Set<String> PLATFORM_ROLES = Set.of("OPERATING_ENTITY", "FINANCE_ENTITY", "TAX_AUTHORITY",
        "CONSUMER", "PLATFORM");

    private FactBalanceMappingRule() {
    }

    /**
     * 把一条经济事实映射为余额变化
     *
     * @param fact 经济事实
     * @return 按明细行顺序的余额变化；不涉及 Owner 余额的事实为空列表
     */
    public static List<BalanceEffect> effects(FactRecord fact) {
        List<BalanceEffect> effects = new ArrayList<>();
        if (PAYMENT.equals(fact.factType())) {
            addEffect(effects, fact, 0, GROSS_AMOUNT, CHANNEL, null, EntrySide.DEBIT, fact.grossAmount(), false);
        }
        for (FactRecord.Line line : fact.lines()) {
            boolean reversal = REVERSAL.equals(line.direction());
            if (line.payerRole() == null && line.payeeRole() == null) {
                String implicit = IMPLICIT_PAYEE.get(line.feeTypeCode());
                if (implicit != null && !PAYMENT.equals(fact.factType())) {
                    throw reject(fact, line.lineNo(), "非交易事实的 Owner 费用类型 " + line.feeTypeCode() + " 缺少付方 / 收方");
                }
                if (implicit != null) {
                    addEffect(effects, fact, line.lineNo(), line.feeTypeCode(), implicit, null, EntrySide.CREDIT,
                        line.amount(), reversal);
                }
                continue;
            }
            addEffect(effects, fact, line.lineNo(), line.feeTypeCode(), line.payerRole(), line.payerId(),
                EntrySide.DEBIT, line.amount(), reversal);
            addEffect(effects, fact, line.lineNo(), line.feeTypeCode(), line.payeeRole(), line.payeeId(),
                EntrySide.CREDIT, line.amount(), reversal);
        }
        return effects;
    }

    /** 一个付方或收方的余额变化；平台侧角色与零金额不产生变化。 */
    static void addEffect(List<BalanceEffect> effects, FactRecord fact, int lineNo, String feeType, String role,
        String partyId, EntrySide side, BigDecimal amount, boolean reversal) {
        if (role == null || PLATFORM_ROLES.contains(role)) {
            return;
        }
        if (amount == null || amount.signum() < 0) {
            throw reject(fact, lineNo, "金额缺失或为负");
        }
        if (amount.signum() == 0) {
            return;
        }
        Map<String, Target> byFee = TARGETS.get(role);
        if (byFee == null) {
            throw reject(fact, lineNo, "未知付方 / 收方角色 " + role);
        }
        Target target = byFee.get(feeType);
        if (target == null) {
            throw reject(fact, lineNo, "费用类型 " + feeType + " 对角色 " + role + " 没有余额映射");
        }
        EntrySide entrySide = reversal ? opposite(side) : side;
        effects.add(new BalanceEffect(lineNo, target.accountCode, target.ownerType, ownerId(fact, lineNo, role, partyId),
            target.bucket, target.normalSide, entrySide, amount));
    }

    /** Owner 标识：CHANNEL 取事实头渠道清算账户；CLIENT / LOCAL_PARTNER 取行内标识，交易事实取事实头主体。 */
    static String ownerId(FactRecord fact, int lineNo, String role, String partyId) {
        Long header = switch (role) {
            case CLIENT -> fact.clientId();
            case LOCAL_PARTNER -> fact.localPartnerId();
            default -> fact.channelAccountId();
        };
        String ownerId = CHANNEL.equals(role) || partyId == null ? (header == null ? null : header.toString()) : partyId;
        if (ownerId == null || !DECIMAL_ID.matcher(ownerId).matches()) {
            throw reject(fact, lineNo, "角色 " + role + " 缺少合法的 Owner 标识");
        }
        return ownerId;
    }

    /** 借贷互换。 */
    static EntrySide opposite(EntrySide side) {
        return side == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
    }

    /** 带事实与行号的业务拒绝。 */
    static BizCheckFailException reject(FactRecord fact, int lineNo, String reason) {
        return new BizCheckFailException("经济事实无法映射余额: " + fact.factId() + ", 行 " + lineNo + ", " + reason);
    }

    /** 余额账户目标。 */
    record Target(String accountCode, OwnerType ownerType, BalanceBucket bucket, NormalSide normalSide) {
    }
}
