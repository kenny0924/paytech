package com.paytech.ledger.test.builder;

import java.math.BigDecimal;

import com.paytech.account.api.enums.OwnerType;
import com.paytech.billing.api.enums.AmountSource;
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
        return component(AmountSource.CLIENT_PRINCIPAL, amount).party(OwnerType.CLIENT, String.valueOf(clientId));
    }

    /** Client 手续费，归属交易 OE。 */
    public static EconomicComponentBuilder clientFee(String amount, String entityNo) {
        return component(AmountSource.CLIENT_FEE, amount).party(OwnerType.OPERATING_ENTITY, entityNo);
    }

    /** Seller 实收税，归属税务机关。 */
    public static EconomicComponentBuilder sellerTax(String amount, long taxAuthorityId) {
        return component(AmountSource.SELLER_TAX, amount).party(OwnerType.TAX_AUTHORITY,
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
        return component(AmountSource.PARTNER_SHARE, amount).party(OwnerType.LOCAL_PARTNER,
            String.valueOf(localPartnerId));
    }

    /** 平台服务费，归属平台 Finance Entity。 */
    public static EconomicComponentBuilder platformServiceCharge(String amount, long financeEntityId) {
        return component(AmountSource.PLATFORM_SERVICE_CHARGE, amount).party(OwnerType.FINANCE_ENTITY,
            String.valueOf(financeEntityId));
    }

    /** 设置归属方类型与标识。 */
    public EconomicComponentBuilder party(OwnerType partyType, String partyId) {
        line.setPartyType(partyType.getCode());
        line.setPartyId(partyId);
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
