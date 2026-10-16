package com.paytech.finance.test.builder;

import java.math.BigDecimal;
import java.util.Map;

import com.paytech.finance.accounting.core.enums.AmountSource;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.test.dal.row.EconomicComponentRow;

/**
 * 交易经济组件夹具的合法模板：组件类型取金额来源名，basis/raw/posted 同值，来源规则列给出稳定默认值
 * <li>只构造对象，不访问数据库；主键、所属经济结果与组件序号由 Mocker 分配。</li>
 */
public final class EconomicComponentBuilder {
    /** 组件来源规则族：按组件类型给出合理默认。 */
    private static final Map<AmountSource, String> SOURCE_RULE_TYPES = Map.of(
        AmountSource.CLIENT_PRINCIPAL, "CLIENT_PRICING",
        AmountSource.CLIENT_FEE, "CLIENT_PRICING",
        AmountSource.SELLER_TAX, "TAX_RULE",
        AmountSource.CHANNEL_COST, "CHANNEL_COST",
        AmountSource.PARTNER_SHARE, "PARTNER_SHARE",
        AmountSource.PLATFORM_SERVICE_CHARGE, "PLATFORM_SERVICE_CHARGE");

    private final EconomicComponentRow row = new EconomicComponentRow();

    private EconomicComponentBuilder() {
    }

    /** 指定类型与金额、无归属方的组件模板。 */
    public static EconomicComponentBuilder component(AmountSource type, String amount) {
        EconomicComponentBuilder builder = new EconomicComponentBuilder();
        EconomicComponentRow row = builder.row;
        BigDecimal value = new BigDecimal(amount);
        row.setComponentType(type.getCode());
        row.setBasisAmount(value);
        row.setRawAmount(value);
        row.setPostedAmount(value);
        row.setEffectDirection("CHARGE");
        row.setSourceRuleType(SOURCE_RULE_TYPES.get(type));
        row.setSourceRuleVersionId(970001L);
        row.setSourceComponentCode(type.getCode());
        row.setSourceTriggerCode("PAYMENT_SUCCEEDED");
        row.setSourceCalculationBase("GROSS_AMOUNT");
        row.setCalculationMethod("FIXED_AMOUNT");
        row.setRoundingMode("HALF_UP");
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
        row.setPartyType(partyType.getCode());
        row.setPartyId(partyId);
        return this;
    }

    /** 返回构造好的夹具行（未分配主键、经济结果与序号）。 */
    public EconomicComponentRow build() {
        return row;
    }
}
