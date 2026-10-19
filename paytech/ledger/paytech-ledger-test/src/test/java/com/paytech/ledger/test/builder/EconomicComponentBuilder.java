package com.paytech.ledger.test.builder;

import java.math.BigDecimal;

import com.paytech.billing.api.enums.AmountSource;
import com.paytech.billing.api.enums.FactLineDirection;
import com.paytech.billing.api.enums.FactPartyRole;
import com.paytech.billing.api.fact.EconomicFactLinePayload;

/**
 * 经济事实明细行模板：组件类型取金额来源代码，金额为已归整的 posted_amount
 * <li>只构造对象，不访问数据库；行号由 {@link TransactionEconomicsBuilder#build} 分配。</li>
 */
public final class EconomicComponentBuilder {
    private final EconomicFactLinePayload line = new EconomicFactLinePayload();

    private EconomicComponentBuilder() {
    }

    /** 指定类型与金额、无归属方的明细行模板。 */
    public static EconomicComponentBuilder component(AmountSource type, String amount) {
        EconomicComponentBuilder builder = new EconomicComponentBuilder();
        builder.line.setComponentType(type.getCode());
        builder.line.setPostedAmount(new BigDecimal(amount));
        return builder;
    }

    /** Client 本金，归属 Client。 */
    public static EconomicComponentBuilder clientPrincipal(String amount, long clientId) {
        return component(AmountSource.CLIENT_PRINCIPAL, amount).party(FactPartyRole.CLIENT, String.valueOf(clientId));
    }

    /** Client 手续费，归属交易 OE。 */
    public static EconomicComponentBuilder clientFee(String amount, String entityNo) {
        return component(AmountSource.CLIENT_FEE, amount).party(FactPartyRole.OPERATING_ENTITY, entityNo);
    }

    /** Seller 实收税，归属税务机关。 */
    public static EconomicComponentBuilder sellerTax(String amount, long taxAuthorityId) {
        return component(AmountSource.SELLER_TAX, amount).party(FactPartyRole.TAX_AUTHORITY,
            String.valueOf(taxAuthorityId));
    }

    /** Seller 实收税，缺少税务机关归属。 */
    public static EconomicComponentBuilder sellerTaxWithoutAuthority(String amount) {
        return component(AmountSource.SELLER_TAX, amount);
    }

    /** 预计渠道成本，无归属方。 */
    public static EconomicComponentBuilder channelCost(String amount) {
        return component(AmountSource.CHANNEL_COST, amount);
    }

    /** Local Partner 分润，归属 Partner。 */
    public static EconomicComponentBuilder partnerShare(String amount, long localPartnerId) {
        return component(AmountSource.PARTNER_SHARE, amount).party(FactPartyRole.LOCAL_PARTNER,
            String.valueOf(localPartnerId));
    }

    /** 平台服务费，归属平台 Finance Entity。 */
    public static EconomicComponentBuilder platformServiceCharge(String amount, long financeEntityId) {
        return component(AmountSource.PLATFORM_SERVICE_CHARGE, amount).party(FactPartyRole.FINANCE_ENTITY,
            String.valueOf(financeEntityId));
    }

    /**
     * 人工 / 成本调整明细行：费用类型、付方、收方与方向；归属方等于收方（第 11a 步约定）
     *
     * @param type 费用类型
     * @param payerRole 付方角色，可为 null（缺失场景）
     * @param payerId 付方标识
     * @param payeeRole 收方角色，可为 null（缺失场景）
     * @param payeeId 收方标识
     * @param direction 影响方向
     * @param amount 已归整金额
     */
    public static EconomicComponentBuilder adjustment(AmountSource type, FactPartyRole payerRole, String payerId,
        FactPartyRole payeeRole, String payeeId, FactLineDirection direction, String amount) {
        EconomicComponentBuilder builder = component(type, amount);
        builder.line.setPayerRole(payerRole == null ? null : payerRole.getCode());
        builder.line.setPayerId(payerId);
        builder.line.setPayeeRole(payeeRole == null ? null : payeeRole.getCode());
        builder.line.setPayeeId(payeeId);
        builder.line.setPartyType(payeeRole == null ? null : payeeRole.getCode());
        builder.line.setPartyId(payeeId);
        builder.line.setDirection(direction.getCode());
        return builder;
    }

    /** 设置归属方类型与标识；归属方即收方（billing 登记约定，详情接口只返回付方 / 收方）。 */
    public EconomicComponentBuilder party(FactPartyRole partyType, String partyId) {
        line.setPartyType(partyType.getCode());
        line.setPartyId(partyId);
        line.setPayeeRole(partyType.getCode());
        line.setPayeeId(partyId);
        return this;
    }

    /** 覆盖已归整金额，可为 null（载荷缺失场景）。 */
    public EconomicComponentBuilder postedAmount(String amount) {
        line.setPostedAmount(amount == null ? null : new BigDecimal(amount));
        return this;
    }

    /** 返回指定行号的明细行。 */
    EconomicFactLinePayload build(int lineNo) {
        line.setLineNo(lineNo);
        return line;
    }
}
