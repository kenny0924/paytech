package com.paytech.billing.test.builder;

import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.CLIENT_PRICING_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.DEFAULT_CALENDAR;
import static com.paytech.billing.test.builder.PaymentFactConstants.END_OF_DAY_CUTOFF;
import static com.paytech.billing.test.builder.PaymentFactConstants.ENTITY_NO;
import static com.paytech.billing.test.builder.PaymentFactConstants.LOCAL_PARTNER_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_AGREEMENT_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PARTNER_SHARE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.PAYMENT_METHOD;
import static com.paytech.billing.test.builder.PaymentFactConstants.PLATFORM_CHARGE_VERSION_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.SERVICE_CHARGE_PRICE_ID;
import static com.paytech.billing.test.builder.PaymentFactConstants.USD;

import java.util.ArrayList;
import java.util.List;

import com.paytech.commercial.api.constant.CommercialCodes;
import com.paytech.commercial.api.enums.FeeTaxTreatment;
import com.paytech.commercial.api.enums.PricingModel;
import com.paytech.commercial.api.price.response.CommercialPriceComponentResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceResponseBody;
import com.paytech.commercial.api.price.response.CommercialPriceSettlementResponseBody;

/**
 * commercial 价格命中响应：clientPricing() / partnerShare() / serviceCharge() 给出各自冻结版本与价格 ID 的默认价格
 * <li>默认没有组件，用例经 {@link #components(CommercialPriceComponentResponseBody...)} 按顺序给出并自动编号；Client 价格默认结算为
 * DEFAULT 日历、UTC、截止 86399、T+1。</li>
 */
public final class CommercialPriceResponseBuilder {
    /** 默认结算工作日数。 */
    public static final int DEFAULT_SETTLEMENT_DAYS = 1;

    /** 结算时区 UTC。 */
    public static final String UTC = "UTC";

    private final CommercialPriceResponseBody body = new CommercialPriceResponseBody();

    /** 只能从模板开始。 */
    private CommercialPriceResponseBuilder() {
    }

    /** Client 定价价格（含默认结算四字段）。 */
    public static CommercialPriceResponseBuilder clientPricing() {
        CommercialPriceResponseBuilder builder = base(CommercialCodes.TermType.CLIENT_PRICING, CLIENT_AGREEMENT_ID,
            CLIENT_PRICING_VERSION_ID, CLIENT_PRICE_ID);
        builder.body.setClientId(Long.toString(CLIENT_ID));
        return builder.settlement(DEFAULT_SETTLEMENT_DAYS, DEFAULT_CALENDAR, UTC, END_OF_DAY_CUTOFF);
    }

    /** Partner 分润价格。 */
    public static CommercialPriceResponseBuilder partnerShare() {
        CommercialPriceResponseBuilder builder = base(CommercialCodes.TermType.PARTNER_SHARE, PARTNER_AGREEMENT_ID,
            PARTNER_SHARE_VERSION_ID, PARTNER_SHARE_PRICE_ID);
        builder.body.setLocalPartnerId(Long.toString(LOCAL_PARTNER_ID));
        return builder;
    }

    /** 平台服务费价格。 */
    public static CommercialPriceResponseBuilder serviceCharge() {
        CommercialPriceResponseBuilder builder = base(CommercialCodes.TermType.PLATFORM_SERVICE_CHARGE,
            PARTNER_AGREEMENT_ID, PLATFORM_CHARGE_VERSION_ID, SERVICE_CHARGE_PRICE_ID);
        builder.body.setLocalPartnerId(Long.toString(LOCAL_PARTNER_ID));
        return builder;
    }

    /** 父链、画像与币种。 */
    static CommercialPriceResponseBuilder base(String termType, long agreementId, long termVersionId, long priceId) {
        CommercialPriceResponseBuilder builder = new CommercialPriceResponseBuilder();
        CommercialPriceResponseBody body = builder.body;
        body.setTermType(termType);
        body.setAgreementId(Long.toString(agreementId));
        body.setPlanId(Long.toString(termVersionId + 100));
        body.setTermVersionId(Long.toString(termVersionId));
        body.setEntityNo(ENTITY_NO);
        body.setPriceId(Long.toString(priceId));
        body.setPricingProfileCode("CARD_STANDARD");
        body.setPaymentMethodCode(PAYMENT_METHOD);
        body.setPriorityNo(1);
        body.setCurrency(USD);
        body.setPricingModel(PricingModel.BLENDED.getCode());
        body.setFeeTaxTreatment(FeeTaxTreatment.EXCLUSIVE.getCode());
        body.setConditions(List.of());
        body.setComponents(new ArrayList<>());
        return builder;
    }

    /** 按给定顺序设置组件，componentNo 从 1 编号。 */
    public CommercialPriceResponseBuilder components(CommercialPriceComponentResponseBody... components) {
        List<CommercialPriceComponentResponseBody> list = new ArrayList<>(List.of(components));
        for (int index = 0; index < list.size(); index++) {
            list.get(index).setComponentNo(index + 1);
        }
        body.setComponents(list);
        return this;
    }

    /** 结算四字段；任一参数可为 null（负例）。 */
    public CommercialPriceResponseBuilder settlement(Integer businessDays, String calendarCode, String timezone,
        Integer cutoffSecondOfDay) {
        CommercialPriceSettlementResponseBody settlement = new CommercialPriceSettlementResponseBody();
        settlement.setSettlementBusinessDays(businessDays);
        settlement.setBusinessCalendarCode(calendarCode);
        settlement.setSettlementTimezone(timezone);
        settlement.setCutoffSecondOfDay(cutoffSecondOfDay);
        body.setSettlement(settlement);
        return this;
    }

    /** 构建。 */
    public CommercialPriceResponseBody build() {
        return body;
    }
}
