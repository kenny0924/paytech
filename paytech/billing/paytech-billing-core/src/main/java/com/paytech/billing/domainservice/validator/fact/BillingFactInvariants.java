package com.paytech.billing.domainservice.validator.fact;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import com.paytech.billing.api.enums.BillingFactErrorCode;
import com.paytech.billing.api.enums.BillingFactType;
import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.billing.api.fact.BillingFactRefPayload;
import com.paytech.billing.core.enums.BillingCodes;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;

/**
 * 人工登记计费事实的目录约束与登记不变量（技术方案第 2.1 节「人工登记的目录约束」）
 * <li>MANUAL_ADJUSTMENT：clientId 必填、parentFactId 为空、只允许可选 TICKET 引用、组件限六类、targetBucket 只在收方为 CLIENT /
 * LOCAL_PARTNER 时允许。</li>
 * <li>COST_ADJUSTMENT：parentFactId 必填（是否为已批准 PAYMENT 由 Processor 查库确认）、必须有 CLEARING_ACCOUNT 引用（另允许 TICKET）、
 * 只允许 CHANNEL_COST + ADJUSTMENT，付方 / 收方为 CHANNEL ↔ OPERATING_ENTITY，不得给出 targetBucket。</li>
 * <li>行级：金额为正、付方 ≠ 收方；CLIENT / LOCAL_PARTNER / FINANCE_ENTITY / OPERATING_ENTITY 的标识等于头部对应字段，其余角色只校验十进制
 * 标识；合作方二元组（localPartnerId + financeEntityId）全有或全无，PARTNER_SHARE、PLATFORM_SERVICE_CHARGE 要求二元组存在，平台服务费
 * 收方为头部 Finance Entity。</li>
 * <li>只做纯内存校验，不访问数据库；引用类型不被事实类型允许为参数错误；行级违反为 {@link BillingFactErrorCode#FACT_LINE_INVALID}，
 * 头部违反（Client、来源事实、CLEARING_ACCOUNT、合作方二元组不完整）为公共业务拒绝。</li>
 * <li>自动 PAYMENT 事实（技术方案 4.6.4）由 billing 按上游事实组装，任何违反都表示上游数据不一致或组装缺陷，统一为公共业务拒绝。</li>
 */
public final class BillingFactInvariants {
    /** MANUAL_ADJUSTMENT 允许的组件类型。 */
    static final Set<FactComponentType> MANUAL_COMPONENTS = EnumSet.of(FactComponentType.CLIENT_PRINCIPAL,
        FactComponentType.CLIENT_FEE, FactComponentType.PARTNER_SHARE, FactComponentType.PLATFORM_SERVICE_CHARGE,
        FactComponentType.SELLER_TAX, FactComponentType.CHANNEL_COST);

    /** 要求合作方二元组的组件类型。 */
    static final Set<FactComponentType> PARTNER_COMPONENTS =
        EnumSet.of(FactComponentType.PARTNER_SHARE, FactComponentType.PLATFORM_SERVICE_CHARGE);

    /** 可以设置目标余额桶的收方角色（Owner 账户）。 */
    static final Set<FactPartyRole> BUCKET_OWNERS = EnumSet.of(FactPartyRole.CLIENT, FactPartyRole.LOCAL_PARTNER);

    /** 成本调整的付方 / 收方角色。 */
    static final Set<FactPartyRole> COST_PARTIES = EnumSet.of(FactPartyRole.CHANNEL, FactPartyRole.OPERATING_ENTITY);

    /** 必须与头部主体一致的付方 / 收方角色。 */
    static final Set<FactPartyRole> HEADER_ROLES = EnumSet.of(FactPartyRole.CLIENT, FactPartyRole.LOCAL_PARTNER,
        FactPartyRole.FINANCE_ENTITY, FactPartyRole.OPERATING_ENTITY);

    /** 自动 PAYMENT 事实必须具备的引用类型。 */
    static final Set<FactRefType> PAYMENT_REFS = EnumSet.of(FactRefType.ORDER, FactRefType.ORDER_CONTEXT,
        FactRefType.PAYMENT_INTENT, FactRefType.PAYMENT_ATTEMPT, FactRefType.PAYMENT_CHANNEL_ACCOUNT, FactRefType.MID,
        FactRefType.CLEARING_ACCOUNT, FactRefType.CHANNEL_TRANSACTION_REF);

    /** 自动 PAYMENT 事实每类组件固定的付方与收方角色（技术方案 4.6 行目录）。 */
    static final Map<FactComponentType, List<FactPartyRole>> PAYMENT_LINE_PARTIES = Map.of(
        FactComponentType.CLIENT_FEE, List.of(FactPartyRole.CHANNEL, FactPartyRole.OPERATING_ENTITY),
        FactComponentType.CLIENT_PRINCIPAL, List.of(FactPartyRole.CHANNEL, FactPartyRole.CLIENT),
        FactComponentType.PARTNER_SHARE, List.of(FactPartyRole.OPERATING_ENTITY, FactPartyRole.LOCAL_PARTNER),
        FactComponentType.PLATFORM_SERVICE_CHARGE, List.of(FactPartyRole.OPERATING_ENTITY, FactPartyRole.FINANCE_ENTITY),
        FactComponentType.CHANNEL_COST, List.of(FactPartyRole.OPERATING_ENTITY, FactPartyRole.CHANNEL));

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** 纯静态校验。 */
    private BillingFactInvariants() {
    }

    /**
     * 校验一条待登记的人工事实：事实类型目录约束、行级不变量与主体一致性
     *
     * @param fact 由待登记事实头、引用与明细行组装的载荷（成本调整的 Client 已取自来源事实）
     * @throws InvalidParameterException 事实类型不允许人工登记或引用类型不被允许
     * @throws FailException 明细行违反目录约束或行级不变量（FACT_LINE_INVALID）
     * @throws BizCheckFailException 头部违反目录约束或登记不变量
     */
    public static void checkManualRegistration(BillingFactPayload fact) {
        BillingFactType factType = BillingCodes.parse(BillingFactType.class, "计费事实类型", fact.getFactType());
        switch (factType) {
            case MANUAL_ADJUSTMENT -> checkManualAdjustment(fact);
            case COST_ADJUSTMENT -> checkCostAdjustment(fact);
            default -> throw new InvalidParameterException("只能人工登记 MANUAL_ADJUSTMENT 或 COST_ADJUSTMENT: "
                + fact.getFactType());
        }
        checkPartnerConsistency(fact);
        for (BillingFactLinePayload line : fact.getLines()) {
            checkLineAmountAndParties(fact, line);
        }
    }

    /** MANUAL_ADJUSTMENT：Client 必填、无来源事实、只允许 TICKET、组件限定、目标余额桶只给 Owner 收方。 */
    static void checkManualAdjustment(BillingFactPayload fact) {
        if (fact.getClientId() == null) {
            throw new BizCheckFailException("人工调整必须指定 Client: 来源 " + fact.getSourceEventKey());
        }
        if (fact.getParentFactId() != null) {
            throw new BizCheckFailException("人工调整不得指定来源事实: 来源 " + fact.getSourceEventKey());
        }
        checkRefTypes(fact, EnumSet.of(FactRefType.TICKET));
        for (BillingFactLinePayload line : fact.getLines()) {
            FactComponentType component = componentOf(line);
            if (!MANUAL_COMPONENTS.contains(component)) {
                throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
            }
            if (line.getTargetBucket() != null && !BUCKET_OWNERS.contains(roleOf(line.getPayeeRole(), "收方"))) {
                throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
            }
        }
    }

    /** COST_ADJUSTMENT：来源事实必填、必有 CLEARING_ACCOUNT、只登记渠道成本调整、付方 / 收方为渠道与 OE。 */
    static void checkCostAdjustment(BillingFactPayload fact) {
        if (fact.getParentFactId() == null) {
            throw new BizCheckFailException("成本调整必须指定来源支付事实: 来源 " + fact.getSourceEventKey());
        }
        checkRefTypes(fact, EnumSet.of(FactRefType.CLEARING_ACCOUNT, FactRefType.TICKET));
        boolean hasClearingAccount = fact.getRefs().stream()
            .anyMatch(ref -> FactRefType.CLEARING_ACCOUNT.getCode().equals(ref.getRefType()));
        if (!hasClearingAccount) {
            throw new BizCheckFailException("成本调整必须引用 CLEARING_ACCOUNT 渠道清算账户: 来源 " + fact.getSourceEventKey());
        }
        for (BillingFactLinePayload line : fact.getLines()) {
            checkCostLine(line);
        }
    }

    /** 成本调整行：CHANNEL_COST + ADJUSTMENT，付方与收方分别是 CHANNEL 与 OPERATING_ENTITY，不给目标余额桶。 */
    static void checkCostLine(BillingFactLinePayload line) {
        boolean costAdjustment = componentOf(line) == FactComponentType.CHANNEL_COST
            && FactLineDirection.ADJUSTMENT.getCode().equals(line.getDirection());
        if (!costAdjustment) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
        FactPartyRole payer = roleOf(line.getPayerRole(), "付方");
        FactPartyRole payee = roleOf(line.getPayeeRole(), "收方");
        if (!COST_PARTIES.contains(payer) || !COST_PARTIES.contains(payee) || payer == payee) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
        if (line.getTargetBucket() != null) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
    }

    /** 引用类型必须在事实类型允许的集合内，否则为参数错误。 */
    static void checkRefTypes(BillingFactPayload fact, Set<FactRefType> allowed) {
        for (BillingFactRefPayload ref : fact.getRefs()) {
            FactRefType refType = BillingCodes.parse(FactRefType.class, "引用类型", ref.getRefType());
            if (!allowed.contains(refType)) {
                throw new InvalidParameterException(fact.getFactType() + " 不允许引用类型 " + ref.getRefType() + ": 来源 "
                    + fact.getSourceEventKey());
            }
        }
    }

    /** 金额为正、付方与收方不同、头部主体角色的标识与头部一致。 */
    static void checkLineAmountAndParties(BillingFactPayload fact, BillingFactLinePayload line) {
        if (line.getPostedAmount().signum() <= 0) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
        if (line.getPayerRole().equals(line.getPayeeRole()) && line.getPayerId().equals(line.getPayeeId())) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
        checkPartyIdentity(fact, line.getPayerRole(), line.getPayerId(), "付方");
        checkPartyIdentity(fact, line.getPayeeRole(), line.getPayeeId(), "收方");
    }

    /** 付方或收方是头部主体角色时标识必须等于头部主体，其余角色只校验十进制标识格式。 */
    static void checkPartyIdentity(BillingFactPayload fact, String role, String id, String side) {
        FactPartyRole partyRole = roleOf(role, side);
        boolean valid = HEADER_ROLES.contains(partyRole) ? Objects.equals(headerIdentity(fact, partyRole), id)
            : DECIMAL_ID.matcher(id).matches();
        if (!valid) {
            throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
        }
    }

    /** 合作方二元组全有或全无；合作方组件要求二元组存在；平台服务费收方必须是头部 Finance Entity。 */
    static void checkPartnerConsistency(BillingFactPayload fact) {
        boolean hasPartner = fact.getLocalPartnerId() != null;
        if (hasPartner != (fact.getFinanceEntityId() != null)) {
            throw new BizCheckFailException("合作方二元组不完整，localPartnerId 与 financeEntityId 必须同有同无: 来源 "
                + fact.getSourceEventKey());
        }
        for (BillingFactLinePayload line : fact.getLines()) {
            FactComponentType component = componentOf(line);
            if (!hasPartner && PARTNER_COMPONENTS.contains(component)) {
                throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
            }
            if (component == FactComponentType.PLATFORM_SERVICE_CHARGE
                && !FactPartyRole.FINANCE_ENTITY.getCode().equals(line.getPayeeRole())) {
                throw new FailException(BillingFactErrorCode.FACT_LINE_INVALID);
            }
        }
    }

    /** 头部给出的主体标识；头部为空时返回空。 */
    static String headerIdentity(BillingFactPayload fact, FactPartyRole role) {
        Object identity = switch (role) {
            case CLIENT -> fact.getClientId();
            case LOCAL_PARTNER -> fact.getLocalPartnerId();
            case FINANCE_ENTITY -> fact.getFinanceEntityId();
            case OPERATING_ENTITY -> fact.getEntityNo();
            default -> null;
        };
        return identity == null ? null : identity.toString();
    }

    /** 行组件类型。 */
    static FactComponentType componentOf(BillingFactLinePayload line) {
        return BillingCodes.parse(FactComponentType.class, "组件类型", line.getComponentType());
    }

    /** 付方或收方角色。 */
    static FactPartyRole roleOf(String role, String side) {
        return BillingCodes.parse(FactPartyRole.class, side + "角色", role);
    }

    /**
     * 校验一条待登记的自动 PAYMENT 事实（技术方案 4.6.4）
     * <li>头部：Client、OE、币种、业务时间与到期时间非空，毛额为正，合作方二元组全有或全无。</li>
     * <li>引用：包含 {@link #PAYMENT_REFS} 全部类型、不含 TICKET；明细行：组件、付方 / 收方角色按行目录，CHARGE、无目标余额桶、金额为正，
     * 主体标识等于头部，渠道标识等于 CLEARING_ACCOUNT；CLIENT_PRINCIPAL 恰一行，Σ CLIENT_PRINCIPAL + Σ CLIENT_FEE = 毛额；PARTNER_SHARE、
     * PLATFORM_SERVICE_CHARGE 只在有合作方时出现。</li>
     *
     * @param fact 由待登记事实头、引用与明细行组装的载荷
     * @throws BizCheckFailException 任一约束不满足
     */
    public static void checkAutoPayment(BillingFactPayload fact) {
        checkPaymentHeader(fact);
        Map<FactRefType, String> refs = paymentRefs(fact);
        for (BillingFactLinePayload line : fact.getLines()) {
            checkPaymentLine(fact, line, refs.get(FactRefType.CLEARING_ACCOUNT));
        }
        checkPaymentTotals(fact);
    }

    /** 头部必填、毛额为正与合作方二元组。 */
    static void checkPaymentHeader(BillingFactPayload fact) {
        boolean complete = fact.getClientId() != null && fact.getEntityNo() != null && fact.getCurrency() != null
            && fact.getBusinessTime() != null && fact.getBalanceAvailableTime() != null
            && fact.getGrossAmount() != null && fact.getGrossAmount().signum() > 0;
        if (!complete) {
            throw paymentRejected(fact, "事实头不完整或毛额不为正");
        }
        if ((fact.getLocalPartnerId() == null) != (fact.getFinanceEntityId() == null)) {
            throw paymentRejected(fact, "合作方二元组不完整");
        }
    }

    /** 引用类型必须覆盖必需集合且不含 TICKET；返回类型到标识的映射。 */
    static Map<FactRefType, String> paymentRefs(BillingFactPayload fact) {
        Map<FactRefType, String> refs = fact.getRefs().stream().collect(Collectors.toMap(
            ref -> BillingCodes.parse(FactRefType.class, "引用类型", ref.getRefType()), BillingFactRefPayload::getRefId));
        if (!refs.keySet().containsAll(PAYMENT_REFS) || refs.containsKey(FactRefType.TICKET)) {
            throw paymentRejected(fact, "引用集合不完整或包含 TICKET");
        }
        return refs;
    }

    /** 一行的组件、角色、方向、金额与主体标识。 */
    static void checkPaymentLine(BillingFactPayload fact, BillingFactLinePayload line, String clearingAccountId) {
        List<FactPartyRole> parties = PAYMENT_LINE_PARTIES.get(componentOf(line));
        boolean valid = parties != null && parties.get(0) == roleOf(line.getPayerRole(), "付方")
            && parties.get(1) == roleOf(line.getPayeeRole(), "收方")
            && FactLineDirection.CHARGE.getCode().equals(line.getDirection()) && line.getTargetBucket() == null
            && line.getPostedAmount().signum() > 0
            && paymentPartyMatches(fact, line.getPayerRole(), line.getPayerId(), clearingAccountId)
            && paymentPartyMatches(fact, line.getPayeeRole(), line.getPayeeId(), clearingAccountId);
        if (!valid) {
            throw paymentRejected(fact, "明细行 " + line.getLineNo() + " 不符合行目录");
        }
    }

    /** 渠道角色的标识是清算账户，其余角色的标识等于头部主体。 */
    static boolean paymentPartyMatches(BillingFactPayload fact, String role, String id, String clearingAccountId) {
        FactPartyRole partyRole = roleOf(role, "主体");
        if (partyRole == FactPartyRole.CHANNEL) {
            return Objects.equals(clearingAccountId, id);
        }
        return Objects.equals(headerIdentity(fact, partyRole), id);
    }

    /** 本金恰一行、本金与手续费之和等于毛额、合作方组件只在有合作方时出现。 */
    static void checkPaymentTotals(BillingFactPayload fact) {
        long principalLines = fact.getLines().stream()
            .filter(line -> componentOf(line) == FactComponentType.CLIENT_PRINCIPAL).count();
        BigDecimal clientTotal = fact.getLines().stream()
            .filter(line -> componentOf(line) == FactComponentType.CLIENT_PRINCIPAL
                || componentOf(line) == FactComponentType.CLIENT_FEE)
            .map(BillingFactLinePayload::getPostedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (principalLines != 1 || clientTotal.compareTo(fact.getGrossAmount()) != 0) {
            throw paymentRejected(fact, "本金必须恰一行且本金与手续费之和等于毛额");
        }
        boolean partnerLines = fact.getLines().stream().anyMatch(line -> PARTNER_COMPONENTS.contains(componentOf(line)));
        if (partnerLines && fact.getLocalPartnerId() == null) {
            throw paymentRejected(fact, "无合作方的事实不得有分润或平台服务费行");
        }
    }

    /** 带来源事件键的自动 PAYMENT 事实拒绝。 */
    static BizCheckFailException paymentRejected(BillingFactPayload fact, String reason) {
        return new BizCheckFailException("自动 PAYMENT 事实不满足登记约束: " + reason + ", 来源 " + fact.getSourceEventKey());
    }
}
