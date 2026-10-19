package com.paytech.billing.domainservice.validator.fact;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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
 * <li>只做纯内存校验，不访问数据库；引用类型不被事实类型允许为参数错误，其余为业务拒绝。</li>
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

    private static final Pattern DECIMAL_ID = Pattern.compile("[1-9][0-9]{0,18}");

    /** 纯静态校验。 */
    private BillingFactInvariants() {
    }

    /**
     * 校验一条待登记的人工事实：事实类型目录约束、行级不变量与主体一致性
     *
     * @param fact 由待登记事实头、引用与明细行组装的载荷（成本调整的 Client 已取自来源事实）
     * @throws InvalidParameterException 事实类型不允许人工登记或引用类型不被允许
     * @throws BizCheckFailException 违反目录约束或登记不变量
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
                throw new BizCheckFailException("人工调整不允许组件类型 " + component.getCode() + ": 行 " + line.getLineNo()
                    + ", 来源 " + fact.getSourceEventKey());
            }
            if (line.getTargetBucket() != null && !BUCKET_OWNERS.contains(roleOf(line.getPayeeRole(), "收方"))) {
                throw new BizCheckFailException("目标余额桶只用于收方为 CLIENT 或 LOCAL_PARTNER 的行: 行 " + line.getLineNo()
                    + ", 收方 " + line.getPayeeRole() + ", 来源 " + fact.getSourceEventKey());
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
            checkCostLine(fact, line);
        }
    }

    /** 成本调整行：CHANNEL_COST + ADJUSTMENT，付方与收方分别是 CHANNEL 与 OPERATING_ENTITY，不给目标余额桶。 */
    static void checkCostLine(BillingFactPayload fact, BillingFactLinePayload line) {
        boolean costAdjustment = componentOf(line) == FactComponentType.CHANNEL_COST
            && FactLineDirection.ADJUSTMENT.getCode().equals(line.getDirection());
        if (!costAdjustment) {
            throw new BizCheckFailException("成本调整只能登记 CHANNEL_COST + ADJUSTMENT 明细: 行 " + line.getLineNo() + ", "
                + line.getComponentType() + "/" + line.getDirection() + ", 来源 " + fact.getSourceEventKey());
        }
        FactPartyRole payer = roleOf(line.getPayerRole(), "付方");
        FactPartyRole payee = roleOf(line.getPayeeRole(), "收方");
        if (!COST_PARTIES.contains(payer) || !COST_PARTIES.contains(payee) || payer == payee) {
            throw new BizCheckFailException("成本调整的付方与收方必须是 CHANNEL 与 OPERATING_ENTITY: 行 " + line.getLineNo()
                + ", " + line.getPayerRole() + " → " + line.getPayeeRole() + ", 来源 " + fact.getSourceEventKey());
        }
        if (line.getTargetBucket() != null) {
            throw new BizCheckFailException("目标余额桶只用于人工调整: 行 " + line.getLineNo() + ", 来源 "
                + fact.getSourceEventKey());
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
            throw new BizCheckFailException("计费事实明细金额必须为正: 行 " + line.getLineNo() + ", 金额 "
                + line.getPostedAmount().toPlainString());
        }
        if (line.getPayerRole().equals(line.getPayeeRole()) && line.getPayerId().equals(line.getPayeeId())) {
            throw new BizCheckFailException("计费事实明细付方与收方不能相同: 行 " + line.getLineNo() + ", "
                + line.getPayerRole() + "/" + line.getPayerId());
        }
        checkPartyIdentity(fact, line, line.getPayerRole(), line.getPayerId(), "付方");
        checkPartyIdentity(fact, line, line.getPayeeRole(), line.getPayeeId(), "收方");
    }

    /** 付方或收方是头部主体角色时标识必须等于头部主体，其余角色只校验十进制标识格式。 */
    static void checkPartyIdentity(BillingFactPayload fact, BillingFactLinePayload line, String role, String id,
        String side) {
        FactPartyRole partyRole = roleOf(role, side);
        boolean valid = HEADER_ROLES.contains(partyRole) ? Objects.equals(headerIdentity(fact, partyRole), id)
            : DECIMAL_ID.matcher(id).matches();
        if (!valid) {
            throw new BizCheckFailException("计费事实" + side + "与头部主体不一致: 行 " + line.getLineNo() + ", " + side + " "
                + role + "/" + id + ", 来源 " + fact.getSourceEventKey());
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
                throw new BizCheckFailException("计费事实缺少合作方二元组，不能登记 " + component.getCode() + ": 行 "
                    + line.getLineNo() + ", 来源 " + fact.getSourceEventKey());
            }
            if (component == FactComponentType.PLATFORM_SERVICE_CHARGE
                && !FactPartyRole.FINANCE_ENTITY.getCode().equals(line.getPayeeRole())) {
                throw new BizCheckFailException("平台服务费收方必须是 Finance Entity " + fact.getFinanceEntityId() + ": 行 "
                    + line.getLineNo() + ", 来源 " + fact.getSourceEventKey());
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
}
