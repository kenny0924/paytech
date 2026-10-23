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
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.foundation.common.error.BizCheckFailException;

/**
 * 计费事实到 Owner 账户流水的映射规则（technique 2.1「account 规则与目录」：金额组件 × 付方 / 收方角色 → Owner 账户类型 / Owner / 余额桶）
 * <li>每条明细行都必须给出付方与收方（BillingFactRecorded 载荷的单一路径），缺任一方即拒绝整条事实。</li>
 * <li>只有 CLIENT、LOCAL_PARTNER 两个角色对应 account 明细账；渠道、经营主体、Finance Entity、税务机关只在 ledger 记账，在本表登记的金额组件上
 * 允许出现但不产生流水；CONSUMER、CASH_ACCOUNT、FX_ACCOUNT 角色与其余组合拒绝，使 ledger 与 account 对同一事实同时接受或同时拒绝。</li>
 * <li>PAYMENT 事实按资金实际流向写行：渠道（清算账户）是本金（→ Client）与 Client 手续费（→ 经营主体）的付方，毛额在渠道处一次拆分，
 * Client 只作为本金收方出现；平台服务费由经营主体付给 Finance Entity，不动 Partner 余额。</li>
 * <li>收方记贷方、付方记借方，余额桶固定为 PENDING（行的 targetBucket 本期不参与映射）；方向为 REVERSAL 时借贷互换。</li>
 * <li>COST_ADJUSTMENT 只允许渠道与经营主体之间的 CHANNEL_COST，不影响 Owner，生成 0 流水批次。</li>
 * <li>Owner 角色出现在没有映射的金额组件上时拒绝整条事实（Fail-Closed），不静默丢弃 Owner 影响。</li>
 * <li>纯内存规则，不访问数据库；金额与舍入由 billing 一次决定，这里只拒绝负数。</li>
 */
public final class FactBalanceMappingRule {
    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** Owner 角色 → 金额组件 → Owner 账户类型（余额桶固定为 PENDING）。 */
    private static final Map<String, Map<String, OwnerAccountType>> TARGETS = Map.of(
        FactPartyRole.CLIENT.getCode(), Map.of(FactComponentType.CLIENT_PRINCIPAL.getCode(), OwnerAccountType.CLIENT_PAYABLE,
            FactComponentType.CLIENT_FEE.getCode(), OwnerAccountType.CLIENT_PAYABLE),
        FactPartyRole.LOCAL_PARTNER.getCode(), Map.of(FactComponentType.PARTNER_SHARE.getCode(),
            OwnerAccountType.PARTNER_PAYABLE));

    /** 成本调整允许的付方 / 收方角色：渠道与经营主体。 */
    private static final Set<String> COST_ROLES = Set.of(FactPartyRole.CHANNEL.getCode(),
        FactPartyRole.OPERATING_ENTITY.getCode());

    /**
     * 平台侧角色 → 允许的金额组件：不产生 Owner 流水，但必须与 ledger 规则包（STANDARD_ADJUSTMENT、STANDARD_TRANSACTION）的平台侧规则行一一对应；
     * 渠道作为 PAYMENT 本金与手续费的付方、成本调整与渠道成本的收付方；
     * 不在其中的组合（含 CONSUMER、CASH_ACCOUNT、FX_ACCOUNT 角色）两边一致拒绝。
     */
    private static final Map<String, Set<String>> PLATFORM_SIDE_FEES = Map.of(
        FactPartyRole.OPERATING_ENTITY.getCode(), Set.of(FactComponentType.CLIENT_PRINCIPAL.getCode(),
            FactComponentType.CLIENT_FEE.getCode(), FactComponentType.PARTNER_SHARE.getCode(),
            FactComponentType.CHANNEL_COST.getCode(), FactComponentType.SELLER_TAX.getCode(),
            FactComponentType.PLATFORM_SERVICE_CHARGE.getCode()),
        FactPartyRole.FINANCE_ENTITY.getCode(), Set.of(FactComponentType.PLATFORM_SERVICE_CHARGE.getCode()),
        FactPartyRole.TAX_AUTHORITY.getCode(), Set.of(FactComponentType.SELLER_TAX.getCode()),
        FactPartyRole.CHANNEL.getCode(), Set.of(FactComponentType.CHANNEL_COST.getCode(),
            FactComponentType.CLIENT_PRINCIPAL.getCode(), FactComponentType.CLIENT_FEE.getCode()));

    private FactBalanceMappingRule() {
    }

    /**
     * 把一条计费事实映射为 Owner 账户流水
     *
     * @param fact 计费事实
     * @return 按明细行顺序的流水；不涉及 Owner 的事实为空列表
     * @throws BizCheckFailException 缺付方 / 收方，或角色、金额组件、金额不合法
     */
    public static List<BalanceEffect> effects(FactRecord fact) {
        List<BalanceEffect> effects = new ArrayList<>();
        for (FactRecord.Line line : fact.lines()) {
            boolean reversal = FactLineDirection.REVERSAL.getCode().equals(line.direction());
            checkPartyLine(fact, line);
            addEffect(effects, fact, line, line.payerRole(), line.payerId(), EntrySide.DEBIT, reversal);
            addEffect(effects, fact, line, line.payeeRole(), line.payeeId(), EntrySide.CREDIT, reversal);
        }
        return effects;
    }

    /** 明细行的付方 / 收方都必须给出；成本调整只允许渠道成本在渠道与经营主体之间。 */
    static void checkPartyLine(FactRecord fact, FactRecord.Line line) {
        if (line.payerRole() == null || line.payeeRole() == null) {
            throw reject(fact, line.lineNo(), "明细行缺少付方或收方");
        }
        if (!BillingFactType.COST_ADJUSTMENT.getCode().equals(fact.factType())) {
            return;
        }
        boolean costRoles = COST_ROLES.containsAll(Set.of(line.payerRole(), line.payeeRole()));
        if (!FactComponentType.CHANNEL_COST.getCode().equals(line.feeTypeCode()) || !costRoles) {
            throw reject(fact, line.lineNo(), "成本调整只允许渠道与经营主体之间的 CHANNEL_COST");
        }
    }

    /** 一个付方或收方的流水；平台侧角色与零金额不产生流水，平台侧未登记的组合拒绝。 */
    static void addEffect(List<BalanceEffect> effects, FactRecord fact, FactRecord.Line line, String role,
        String partyId, EntrySide side, boolean reversal) {
        Set<String> platformFees = PLATFORM_SIDE_FEES.get(role);
        if (platformFees != null) {
            if (!platformFees.contains(line.feeTypeCode())) {
                throw reject(fact, line.lineNo(), "金额组件 " + line.feeTypeCode() + " 对角色 " + role + " 没有记账规则");
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

    /** Owner 角色与金额组件对应的账户类型；未知角色或未映射的金额组件拒绝。 */
    static OwnerAccountType target(FactRecord fact, FactRecord.Line line, String role) {
        Map<String, OwnerAccountType> byFee = TARGETS.get(role);
        if (byFee == null) {
            throw reject(fact, line.lineNo(), "未知付方 / 收方角色 " + role);
        }
        OwnerAccountType target = byFee.get(line.feeTypeCode());
        if (target == null) {
            throw reject(fact, line.lineNo(), "金额组件 " + line.feeTypeCode() + " 对角色 " + role + " 没有余额映射");
        }
        return target;
    }

    /** Owner 标识：取行内付方 / 收方标识；行内缺失时取事实头的 Client / Local Partner。 */
    static String ownerId(FactRecord fact, int lineNo, String role, String partyId) {
        Long header = FactPartyRole.CLIENT.getCode().equals(role) ? fact.clientId() : fact.localPartnerId();
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
        return new BizCheckFailException("计费事实无法映射余额: " + fact.factId() + ", 行 " + lineNo + ", " + reason);
    }
}
