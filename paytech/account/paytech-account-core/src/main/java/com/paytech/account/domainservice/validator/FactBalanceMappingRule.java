package com.paytech.account.domainservice.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.EntrySide;
import com.paytech.account.api.enums.OwnerAccountType;
import com.paytech.account.domain.BalanceEffect;
import com.paytech.account.domain.FactRecord;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 经济事实到 Owner 账户流水的映射规则（technique 2.1「account 规则与目录」：费用类型 × 付方 / 收方角色 → Owner 账户类型 / Owner / 余额桶）
 * <li>只有 CLIENT、LOCAL_PARTNER 两个角色对应 account 明细账；渠道、经营主体、Finance Entity、税务机关只在 ledger 记账，在本表登记的费用类型上
 * 允许出现但不产生流水；PLATFORM、CONSUMER 角色与其余组合拒绝，使 ledger 与 account 对同一事实同时接受或同时拒绝。</li>
 * <li>收方记贷方、付方记借方；方向为 REVERSAL 时借贷互换。</li>
 * <li>交易事实（PAYMENT）的明细行没有付方 / 收方，按费用类型的隐含收方映射：CLIENT_PRINCIPAL 为 Client 应付、PARTNER_SHARE 为 Partner 应付，
 * 都记 PENDING 贷方；其余费用类型（含 CHANNEL_COST、CLIENT_FEE、平台服务费）不涉及 Owner 账户。</li>
 * <li>COST_ADJUSTMENT 只允许渠道与经营主体之间的 CHANNEL_COST，不影响 Owner，生成 0 流水批次。</li>
 * <li>Owner 角色出现在没有映射的费用类型上时拒绝整条事实（Fail-Closed），不静默丢弃 Owner 影响。</li>
 * <li>纯内存规则，不访问数据库；金额与舍入由 billing 一次决定，这里只拒绝负数。</li>
 */
public final class FactBalanceMappingRule {
    /** 交易事实类型代码（billing EconomicsType.PAYMENT）。 */
    static final String PAYMENT = "PAYMENT";

    /** 成本调整事实类型代码（billing EconomicsType.COST_ADJUSTMENT）。 */
    static final String COST_ADJUSTMENT = "COST_ADJUSTMENT";

    /** 冲回方向代码（billing FactLineDirection.REVERSAL）。 */
    static final String REVERSAL = "REVERSAL";

    /** 付方 / 收方中对应 Owner 账户的角色代码（billing FactPartyRole）。 */
    static final String CLIENT = "CLIENT";

    static final String LOCAL_PARTNER = "LOCAL_PARTNER";

    /** 渠道角色代码：只在 ledger 记账。 */
    static final String CHANNEL = "CHANNEL";

    /** 经营主体角色代码：只在 ledger 记账。 */
    static final String OPERATING_ENTITY = "OPERATING_ENTITY";

    /** 渠道成本费用类型代码。 */
    static final String CHANNEL_COST = "CHANNEL_COST";

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** Owner 角色 → 费用类型 → Owner 账户类型（余额桶固定为 PENDING）。 */
    private static final Map<String, Map<String, OwnerAccountType>> TARGETS = Map.of(
        CLIENT, Map.of("CLIENT_PRINCIPAL", OwnerAccountType.CLIENT_PAYABLE, "CLIENT_FEE", OwnerAccountType.CLIENT_PAYABLE),
        LOCAL_PARTNER, Map.of("PARTNER_SHARE", OwnerAccountType.PARTNER_PAYABLE));

    /** 交易事实明细行没有付方 / 收方时的隐含 Owner 收方。 */
    private static final Map<String, String> IMPLICIT_PAYEE = Map.of("CLIENT_PRINCIPAL", CLIENT, "PARTNER_SHARE",
        LOCAL_PARTNER);

    /** 非交易事实中必须带付方 / 收方的费用类型：隐含收方的费用类型与渠道成本。 */
    private static final Set<String> PARTY_REQUIRED_FEES = Set.of("CLIENT_PRINCIPAL", "PARTNER_SHARE", CHANNEL_COST);

    /**
     * 平台侧角色 → 允许的费用类型：不产生 Owner 流水，但必须与 ledger STANDARD_ADJUSTMENT 规则包的平台侧规则行一一对应，
     * 不在其中的组合（含 PLATFORM、CONSUMER 角色）两边一致拒绝。
     */
    private static final Map<String, Set<String>> PLATFORM_SIDE_FEES = Map.of(
        OPERATING_ENTITY, Set.of("CLIENT_PRINCIPAL", "CLIENT_FEE", "PARTNER_SHARE", "GROSS_AMOUNT", CHANNEL_COST,
            "SELLER_TAX", "PLATFORM_SERVICE_CHARGE"),
        "FINANCE_ENTITY", Set.of("PLATFORM_SERVICE_CHARGE"),
        "TAX_AUTHORITY", Set.of("SELLER_TAX"),
        CHANNEL, Set.of("GROSS_AMOUNT", CHANNEL_COST));

    private FactBalanceMappingRule() {
    }

    /**
     * 把一条经济事实映射为 Owner 账户流水
     *
     * @param fact 经济事实
     * @return 按明细行顺序的流水；不涉及 Owner 的事实为空列表
     * @throws BizCheckFailException 角色、费用类型或金额不合法
     */
    public static List<BalanceEffect> effects(FactRecord fact) {
        List<BalanceEffect> effects = new ArrayList<>();
        for (FactRecord.Line line : fact.lines()) {
            boolean reversal = REVERSAL.equals(line.direction());
            if (line.payerRole() == null && line.payeeRole() == null) {
                addImplicitEffect(effects, fact, line, reversal);
                continue;
            }
            checkPartyLine(fact, line);
            addEffect(effects, fact, line, line.payerRole(), line.payerId(), EntrySide.DEBIT, reversal);
            addEffect(effects, fact, line, line.payeeRole(), line.payeeId(), EntrySide.CREDIT, reversal);
        }
        return effects;
    }

    /** 没有付方 / 收方的明细行：只有交易事实允许，按隐含收方记贷方。 */
    static void addImplicitEffect(List<BalanceEffect> effects, FactRecord fact, FactRecord.Line line, boolean reversal) {
        boolean payment = PAYMENT.equals(fact.factType());
        if (!payment && PARTY_REQUIRED_FEES.contains(line.feeTypeCode())) {
            throw reject(fact, line.lineNo(), "非交易事实的费用类型 " + line.feeTypeCode() + " 缺少付方 / 收方");
        }
        String implicit = IMPLICIT_PAYEE.get(line.feeTypeCode());
        if (payment && implicit != null) {
            addEffect(effects, fact, line, implicit, null, EntrySide.CREDIT, reversal);
        }
    }

    /** 带付方 / 收方的明细行：两方都必须给出；成本调整只允许渠道成本在渠道与经营主体之间。 */
    static void checkPartyLine(FactRecord fact, FactRecord.Line line) {
        if (line.payerRole() == null || line.payeeRole() == null) {
            throw reject(fact, line.lineNo(), "明细行缺少付方或收方");
        }
        boolean costRoles = Set.of(CHANNEL, OPERATING_ENTITY).containsAll(Set.of(line.payerRole(), line.payeeRole()));
        if (COST_ADJUSTMENT.equals(fact.factType()) && (!CHANNEL_COST.equals(line.feeTypeCode()) || !costRoles)) {
            throw reject(fact, line.lineNo(), "成本调整只允许渠道与经营主体之间的 CHANNEL_COST");
        }
    }

    /** 一个付方或收方的流水；平台侧角色与零金额不产生流水，平台侧未登记的组合拒绝。 */
    static void addEffect(List<BalanceEffect> effects, FactRecord fact, FactRecord.Line line, String role,
        String partyId, EntrySide side, boolean reversal) {
        Set<String> platformFees = PLATFORM_SIDE_FEES.get(role);
        if (platformFees != null) {
            if (!platformFees.contains(line.feeTypeCode())) {
                throw reject(fact, line.lineNo(), "费用类型 " + line.feeTypeCode() + " 对角色 " + role + " 没有记账规则");
            }
            return;
        }
        BigDecimal amount = line.amount();
        if (amount == null || amount.signum() < 0) {
            throw reject(fact, line.lineNo(), "金额缺失或为负");
        }
        if (amount.signum() == 0) {
            return;
        }
        OwnerAccountType target = target(fact, line, role);
        EntrySide entrySide = reversal ? opposite(side) : side;
        effects.add(new BalanceEffect(line.lineNo(), target, ownerId(fact, line.lineNo(), role, partyId),
            BalanceBucket.PENDING, entrySide, amount));
    }

    /** Owner 角色与费用类型对应的账户类型；未知角色或未映射的费用类型拒绝。 */
    static OwnerAccountType target(FactRecord fact, FactRecord.Line line, String role) {
        Map<String, OwnerAccountType> byFee = TARGETS.get(role);
        if (byFee == null) {
            throw reject(fact, line.lineNo(), "未知付方 / 收方角色 " + role);
        }
        OwnerAccountType target = byFee.get(line.feeTypeCode());
        if (target == null) {
            throw reject(fact, line.lineNo(), "费用类型 " + line.feeTypeCode() + " 对角色 " + role + " 没有余额映射");
        }
        return target;
    }

    /** Owner 标识：取行内标识，交易事实取事实头的 Client / Local Partner。 */
    static String ownerId(FactRecord fact, int lineNo, String role, String partyId) {
        Long header = CLIENT.equals(role) ? fact.clientId() : fact.localPartnerId();
        String ownerId = partyId != null ? partyId : header == null ? null : header.toString();
        if (ownerId == null || !DECIMAL_ID.matcher(ownerId).matches()) {
            throw reject(fact, lineNo, "角色 " + role + " 缺少合法的 Owner 标识");
        }
        return ownerId;
    }

    /** 借贷互换。 */
    public static EntrySide opposite(EntrySide side) {
        return side == EntrySide.DEBIT ? EntrySide.CREDIT : EntrySide.DEBIT;
    }

    /** 带事实与行号的业务拒绝。 */
    static BizCheckFailException reject(FactRecord fact, int lineNo, String reason) {
        return new BizCheckFailException("经济事实无法映射余额: " + fact.factId() + ", 行 " + lineNo + ", " + reason);
    }
}
