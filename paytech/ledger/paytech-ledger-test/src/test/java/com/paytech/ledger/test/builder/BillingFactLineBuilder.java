package com.paytech.ledger.test.builder;

import java.math.BigDecimal;

import com.paytech.billing.api.enums.FactComponentType;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.BillingFactLinePayload;
import com.paytech.billing.api.fact.BillingFactPayload;

/**
 * 计费事实明细行模板：组件类型取 billing 金额组件代码，金额为已归整的 posted_amount，每行带付方 / 收方
 * <li>交易事实行按讨论稿用例 C1 的付方 / 收方：本金渠道 → Client、手续费 Client → OE、分润 OE → Partner、服务费 Partner → Finance Entity、
 * 渠道成本 OE → 渠道；税 Client → 税务机关。付方标识取事实头时由 {@link BillingFactBuilder#build} 按头字段填入。</li>
 * <li>只构造对象，不访问数据库；行号由 {@link BillingFactBuilder#build} 分配。</li>
 */
public final class BillingFactLineBuilder {
    /** 渠道角色的测试标识。 */
    public static final String CHANNEL_ID = "77";

    private final BillingFactLinePayload line = new BillingFactLinePayload();

    /** 付方标识取自事实头的角色；为空表示付方标识已显式给出。 */
    private FactPartyRole payerFromHeader;

    private BillingFactLineBuilder() {
    }

    /** 指定类型与金额、无付方 / 收方的明细行模板。 */
    static BillingFactLineBuilder component(FactComponentType type, String amount) {
        BillingFactLineBuilder builder = new BillingFactLineBuilder();
        builder.line.setComponentType(type.getCode());
        builder.line.setPostedAmount(new BigDecimal(amount));
        return builder;
    }

    /** Client 本金：渠道 → Client。 */
    public static BillingFactLineBuilder clientPrincipal(String amount, long clientId) {
        return component(FactComponentType.CLIENT_PRINCIPAL, amount).payer(FactPartyRole.CHANNEL, CHANNEL_ID)
            .payee(FactPartyRole.CLIENT, String.valueOf(clientId));
    }

    /** Client 手续费：Client（事实头）→ 交易 OE。 */
    public static BillingFactLineBuilder clientFee(String amount, String entityNo) {
        return component(FactComponentType.CLIENT_FEE, amount).payerFromHeader(FactPartyRole.CLIENT)
            .payee(FactPartyRole.OPERATING_ENTITY, entityNo);
    }

    /** Seller 实收税：Client（事实头）→ 税务机关。 */
    public static BillingFactLineBuilder sellerTax(String amount, long taxAuthorityId) {
        return component(FactComponentType.SELLER_TAX, amount).payerFromHeader(FactPartyRole.CLIENT)
            .payee(FactPartyRole.TAX_AUTHORITY, String.valueOf(taxAuthorityId));
    }

    /** Seller 实收税（billing 自动 PAYMENT 行目录，tax 技术方案 11.2）：渠道清算账户 → 税务机关。 */
    public static BillingFactLineBuilder channelSellerTax(String amount, long taxAuthorityId) {
        return component(FactComponentType.SELLER_TAX, amount).payer(FactPartyRole.CHANNEL, CHANNEL_ID)
            .payee(FactPartyRole.TAX_AUTHORITY, String.valueOf(taxAuthorityId));
    }

    /** Seller 实收税，缺少税务机关收方。 */
    public static BillingFactLineBuilder sellerTaxWithoutAuthority(String amount) {
        return component(FactComponentType.SELLER_TAX, amount).payerFromHeader(FactPartyRole.CLIENT);
    }

    /** 预计渠道成本：交易 OE（事实头）→ 渠道。 */
    public static BillingFactLineBuilder channelCost(String amount) {
        return component(FactComponentType.CHANNEL_COST, amount).payerFromHeader(FactPartyRole.OPERATING_ENTITY)
            .payee(FactPartyRole.CHANNEL, CHANNEL_ID);
    }

    /** Local Partner 分润：交易 OE（事实头）→ Partner。 */
    public static BillingFactLineBuilder partnerShare(String amount, long localPartnerId) {
        return component(FactComponentType.PARTNER_SHARE, amount).payerFromHeader(FactPartyRole.OPERATING_ENTITY)
            .payee(FactPartyRole.LOCAL_PARTNER, String.valueOf(localPartnerId));
    }

    /** 平台服务费：Partner（事实头）→ 平台 Finance Entity。 */
    public static BillingFactLineBuilder platformServiceCharge(String amount, long financeEntityId) {
        return component(FactComponentType.PLATFORM_SERVICE_CHARGE, amount)
            .payerFromHeader(FactPartyRole.LOCAL_PARTNER)
            .payee(FactPartyRole.FINANCE_ENTITY, String.valueOf(financeEntityId));
    }

    /**
     * 人工 / 成本调整明细行：金额组件、付方、收方与方向
     *
     * @param type 金额组件
     * @param payerRole 付方角色，可为 null（缺失场景）
     * @param payerId 付方标识
     * @param payeeRole 收方角色，可为 null（缺失场景）
     * @param payeeId 收方标识
     * @param direction 影响方向
     * @param amount 已归整金额
     */
    public static BillingFactLineBuilder adjustment(FactComponentType type, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, FactLineDirection direction, String amount) {
        BillingFactLineBuilder builder = component(type, amount);
        builder.line.setPayerRole(payerRole == null ? null : payerRole.getCode());
        builder.line.setPayerId(payerId);
        builder.line.setPayeeRole(payeeRole == null ? null : payeeRole.getCode());
        builder.line.setPayeeId(payeeId);
        builder.line.setDirection(direction.getCode());
        return builder;
    }

    /** 设置付方角色与标识。 */
    BillingFactLineBuilder payer(FactPartyRole role, String partyId) {
        line.setPayerRole(role.getCode());
        line.setPayerId(partyId);
        return this;
    }

    /** 设置付方角色，标识在构造事实时取事实头对应字段。 */
    BillingFactLineBuilder payerFromHeader(FactPartyRole role) {
        line.setPayerRole(role.getCode());
        payerFromHeader = role;
        return this;
    }

    /** 设置收方角色与标识。 */
    BillingFactLineBuilder payee(FactPartyRole role, String partyId) {
        line.setPayeeRole(role.getCode());
        line.setPayeeId(partyId);
        return this;
    }

    /** 覆盖已归整金额，可为 null（载荷缺失场景）。 */
    public BillingFactLineBuilder postedAmount(String amount) {
        line.setPostedAmount(amount == null ? null : new BigDecimal(amount));
        return this;
    }

    /** 返回指定行号的明细行；付方标识取事实头时按头字段填入（头字段为空则保持为空）。 */
    BillingFactLinePayload build(int lineNo, BillingFactPayload fact) {
        line.setLineNo(lineNo);
        if (payerFromHeader != null) {
            line.setPayerId(headerId(payerFromHeader, fact));
        }
        return line;
    }

    /** 事实头中角色对应的标识。 */
    static String headerId(FactPartyRole role, BillingFactPayload fact) {
        Object id = switch (role) {
            case CLIENT -> fact.getClientId();
            case LOCAL_PARTNER -> fact.getLocalPartnerId();
            case OPERATING_ENTITY -> fact.getEntityNo();
            case FINANCE_ENTITY -> fact.getFinanceEntityId();
            default -> throw new IllegalArgumentException("事实头没有角色 " + role.getCode() + " 的标识");
        };
        return id == null ? null : id.toString();
    }
}
